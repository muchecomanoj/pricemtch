import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { FiPlus, FiEdit2, FiTrash2, FiShield } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import DataTable from '../../components/tables/DataTable'
import Button from '../../components/common/Button'
import Modal from '../../components/common/Modal'
import { roleService } from '../../services/roleService'
import { useNotification } from '../../context/NotificationContext'

export default function Roles() {
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()
  const [editing, setEditing] = useState(null) // null=closed, {}=create, {..}=edit

  const { data, isLoading } = useQuery({ queryKey: ['roles'], queryFn: roleService.list })

  // Any role change also refreshes the Users role dropdown (shared ['roles'] key).
  const invalidate = () => qc.invalidateQueries({ queryKey: ['roles'] })

  const saveMut = useMutation({
    mutationFn: (v) => (v.__isEdit ? roleService.update(v.name, v) : roleService.create(v)),
    onSuccess: () => { invalidate(); notify.success('Role saved'); setEditing(null) },
    onError: (e) => notify.error(e.message || 'Could not save role'),
  })

  const removeMut = useMutation({
    mutationFn: (name) => roleService.remove(name),
    onSuccess: () => { invalidate(); notify.success('Role deleted') },
    onError: (e) => notify.error(e.message || 'Could not delete role'),
  })

  const handleDelete = async (r) => {
    if (await confirm({ text: `Delete role "${r.label || r.name}"? Users with this role must be reassigned.` }))
      removeMut.mutate(r.name)
  }

  const columns = [
    { key: 'name', header: 'Role Code', render: (r) => (
      <span className="d-inline-flex align-items-center gap-2 fw-semibold">
        <FiShield className="text-primary" /> {r.name}
      </span>
    ) },
    { key: 'label', header: 'Label', render: (r) => <span className="badge text-bg-primary">{r.label || r.name}</span> },
    { key: 'description', header: 'Description', render: (r) => r.description || <span className="text-muted">—</span> },
    { key: 'actions', header: 'Actions', render: (r) => (
      <div className="d-flex gap-1">
        <button className="btn btn-sm btn-light" title="Edit" onClick={() => setEditing(r)}><FiEdit2 /></button>
        <button className="btn btn-sm btn-light text-danger" title="Delete" onClick={() => handleDelete(r)}><FiTrash2 /></button>
      </div>
    ) },
  ]

  return (
    <>
      <PageHeader
        title="Roles"
        subtitle="Master data — define the roles users can be assigned."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Roles' }]}
        actions={<Button icon={FiPlus} onClick={() => setEditing({})}>Add Role</Button>}
      />

      <Card title={`Roles (${data?.length ?? 0})`}>
        <DataTable columns={columns} rows={data ?? []} loading={isLoading} emptyMessage="No roles defined." />
      </Card>

      <Modal
        show={editing !== null}
        title={editing?.name ? 'Edit Role' : 'Add Role'}
        onClose={() => setEditing(null)}
      >
        {editing !== null && (
          <RoleForm
            defaultValues={editing}
            submitting={saveMut.isPending}
            onSubmit={(v) => saveMut.mutate({ ...editing, ...v, __isEdit: !!editing.name })}
            onCancel={() => setEditing(null)}
          />
        )}
      </Modal>
    </>
  )
}

function RoleForm({ defaultValues, onSubmit, onCancel, submitting }) {
  const isEdit = !!defaultValues?.name
  const { register, handleSubmit, formState: { errors } } = useForm({ defaultValues })

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="row g-3">
      <div className="col-md-6">
        <label className="form-label">Role Code</label>
        <input
          className={`form-control ${errors.name ? 'is-invalid' : ''}`}
          placeholder="e.g. CATEGORY_MANAGER"
          readOnly={isEdit} // code is the identity key — not editable once created
          {...register('name', {
            required: 'Role code is required',
            pattern: { value: /^[A-Z][A-Z0-9_]*$/, message: 'Use UPPER_SNAKE_CASE' },
          })}
        />
        {errors.name && <div className="invalid-feedback">{errors.name.message}</div>}
        {isEdit && <div className="form-text">Role code cannot be changed.</div>}
      </div>
      <div className="col-md-6">
        <label className="form-label">Label</label>
        <input className={`form-control ${errors.label ? 'is-invalid' : ''}`}
          placeholder="e.g. Category Manager"
          {...register('label', { required: 'Label is required' })} />
        {errors.label && <div className="invalid-feedback">{errors.label.message}</div>}
      </div>
      <div className="col-12">
        <label className="form-label">Description</label>
        <textarea className="form-control" rows={2} placeholder="What can this role do?" {...register('description')} />
      </div>
      <div className="col-12 d-flex justify-content-end gap-2">
        <Button variant="light" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={submitting}>Save Role</Button>
      </div>
    </form>
  )
}
