import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FiPlus, FiEdit2, FiTrash2, FiRotateCcw, FiDatabase } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Modal from '../../components/common/Modal'
import DataTable from '../../components/tables/DataTable'
import SearchBar from '../../components/common/SearchBar'
import { useNotification } from '../../context/NotificationContext'

// Generic master-data screen: searchable table + add/edit modal + delete.
//
// `store` is any object exposing list/create/update/remove/reset. Today that is
// the localStorage-backed store; when the CRUD endpoints ship, pass a real
// service with the same shape and this page needs no changes.
export default function MasterDataPage({
  title,
  entity,          // singular noun for copy, e.g. "category"
  storeKey,        // react-query cache key
  store,
  seed = [],       // used only on first load, before any local edits exist
  fields,          // [{ key, label, required, placeholder, help, type }]
  columns,         // DataTable columns
  description,
  readOnly = false,
  readOnlyNote,
}) {
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState(null)   // null = closed, {} = create

  const { data, isLoading } = useQuery({
    queryKey: ['masterData', storeKey],
    queryFn: () => store.list(seed),
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm()

  const open = (row) => {
    setEditing(row || {})
    reset(row || Object.fromEntries(fields.map((f) => [f.key, ''])))
  }

  const refresh = () => qc.invalidateQueries({ queryKey: ['masterData', storeKey] })

  const save = useMutation({
    mutationFn: (values) => (editing?.id ? store.update(editing.id, values) : store.create(values)),
    onSuccess: () => {
      notify.success(`${entity[0].toUpperCase()}${entity.slice(1)} ${editing?.id ? 'updated' : 'added'}`)
      setEditing(null)
      refresh()
    },
    onError: (err) => notify.error(err?.message || `Could not save the ${entity}.`),
  })

  const remove = useMutation({
    mutationFn: (id) => store.remove(id),
    onSuccess: () => { notify.success(`${entity[0].toUpperCase()}${entity.slice(1)} deleted`); refresh() },
    onError: (err) => notify.error(err?.message || `Could not delete the ${entity}.`),
  })

  const resetAll = useMutation({
    mutationFn: () => store.reset(),
    onSuccess: () => { notify.success('Reset to defaults'); refresh() },
  })

  const onDelete = async (row) => {
    const label = row[fields[0].key]
    const ok = await confirm({
      title: `Delete ${label}?`,
      text: `This removes the ${entity} from the list. Products already using it keep their value.`,
      confirmText: 'Delete',
    })
    if (ok) remove.mutate(row.id)
  }

  const onResetAll = async () => {
    const ok = await confirm({
      title: 'Reset to defaults?',
      text: 'Your added and edited rows will be discarded.',
      confirmText: 'Reset',
    })
    if (ok) resetAll.mutate()
  }

  const rows = useMemo(() => {
    const all = data ?? []
    const q = search.trim().toLowerCase()
    if (!q) return all
    return all.filter((r) => fields.some((f) => String(r[f.key] ?? '').toLowerCase().includes(q)))
  }, [data, search, fields])

  const tableColumns = readOnly ? columns : [
    ...columns,
    {
      key: 'actions',
      header: '',
      className: 'text-end',
      render: (r) => (
        <div className="d-inline-flex gap-1">
          <button className="icon-btn" title="Edit" onClick={() => open(r)}><FiEdit2 /></button>
          <button className="icon-btn text-danger" title="Delete" disabled={remove.isPending}
            onClick={() => onDelete(r)}><FiTrash2 /></button>
        </div>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title={title}
        subtitle={description}
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Master Data' }, { label: title }]}
        actions={!readOnly && (
          <div className="d-flex flex-nowrap gap-2">
            <Button variant="light" icon={FiRotateCcw} loading={resetAll.isPending}
              className="flex-shrink-0" onClick={onResetAll}>Reset</Button>
            <Button icon={FiPlus} className="flex-shrink-0" onClick={() => open(null)}>
              Add {entity}
            </Button>
          </div>
        )}
      />

      {/* localStorage, not the server — say so once rather than implying these
          rows are shared with the rest of the team. */}
      <div className="d-flex align-items-start gap-2 text-muted small mb-3">
        <FiDatabase className="flex-shrink-0 mt-1" />
        <span>
          {readOnly
            ? readOnlyNote
            : 'Saved in this browser until the master-data endpoints are available — changes are not shared with other users yet.'}
        </span>
      </div>

      <Card
        title={`${title} (${rows.length})`}
        actions={<SearchBar value={search} onChange={setSearch} placeholder={`Filter ${title.toLowerCase()}…`} />}
      >
        <DataTable columns={tableColumns} rows={rows} loading={isLoading}
          emptyMessage={search ? `No ${title.toLowerCase()} match your filter.` : `No ${title.toLowerCase()} yet.`} />
      </Card>

      <Modal show={editing !== null} onClose={() => setEditing(null)}
        title={editing?.id ? `Edit ${entity}` : `Add ${entity}`}>
        {editing !== null && (
          <form onSubmit={handleSubmit((v) => save.mutate(v))} className="row g-3">
            {fields.map((f) => (
              <div className={f.width || 'col-12'} key={f.key}>
                <label className="form-label" htmlFor={`md-${f.key}`}>
                  {f.label}{f.required && <span className="text-danger ms-1">*</span>}
                </label>
                {f.options ? (
                  <select id={`md-${f.key}`} className={`form-select ${errors[f.key] ? 'is-invalid' : ''}`}
                    {...register(f.key, f.required ? { required: `${f.label} is required.` } : {})}>
                    <option value="">Choose…</option>
                    {f.options.map((o) => <option key={o}>{o}</option>)}
                  </select>
                ) : (
                  <input id={`md-${f.key}`} type={f.type || 'text'} placeholder={f.placeholder}
                    className={`form-control ${errors[f.key] ? 'is-invalid' : ''}`}
                    {...register(f.key, f.required ? { required: `${f.label} is required.` } : {})} />
                )}
                {errors[f.key] && <div className="invalid-feedback">{errors[f.key].message}</div>}
                {f.help && <div className="form-text">{f.help}</div>}
              </div>
            ))}
            <div className="col-12 d-flex justify-content-end gap-2 pt-2 border-top-soft">
              <Button variant="light" type="button" onClick={() => setEditing(null)}>Cancel</Button>
              <Button type="submit" loading={save.isPending}>
                {editing?.id ? 'Save changes' : `Add ${entity}`}
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </>
  )
}
