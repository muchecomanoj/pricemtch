import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useWriteLock } from '../../context/AuthContext'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { FiPlus, FiEdit2, FiTrash2, FiKey } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import DataTable from '../../components/tables/DataTable'
import Button from '../../components/common/Button'
import Modal from '../../components/common/Modal'
import StatusBadge from '../../components/common/StatusBadge'
import { userService } from '../../services/catalogService'
import { useNotification } from '../../context/NotificationContext'
import { ASSIGNABLE_ROLES, ROLE_LABELS, ROLES } from '../../constants'

export default function Users() {
  // Plain write buttons lock themselves while the plan is read-only.
  const writeLock = useWriteLock()
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()
  const [editing, setEditing] = useState(null)
  const { data, isLoading } = useQuery({ queryKey: ['users'], queryFn: userService.list })
  // Access types are a fixed backend enum now (no /roles endpoint).
  const roleLabel = (name) => ROLE_LABELS[name] || name

  // Adding or removing a user moves the seat count on My Subscription.
  const refreshTeam = () => {
    qc.invalidateQueries({ queryKey: ['users'] })
    qc.invalidateQueries({ queryKey: ['client-dashboard'] })
  }
  // Save errors are shown inside the form, not as a toast: the one that
  // matters — the plan's user limit — needs an explanation and a link.
  const saveMut = useMutation({
    mutationFn: (v) => userService.save(v),
    onSuccess: () => { refreshTeam(); notify.success('User saved'); setEditing(null) },
  })
  const removeMut = useMutation({
    mutationFn: (id) => userService.remove(id),
    onSuccess: () => { refreshTeam(); notify.success('User removed') },
    onError: (e) => notify.error(e.message || 'Could not remove the user'),
  })
  const openForm = (u) => { saveMut.reset(); setEditing(u) }

  const handleDelete = async (u) => { if (await confirm({ text: `Remove ${u.name}?` })) removeMut.mutate(u.id) }

  const resetPwMut = useMutation({
    mutationFn: (id) => userService.resetPassword(id),
    onSuccess: () => notify.success('Password reset initiated'),
    onError: (e) => notify.error(e.message || 'Reset failed'),
  })
  const handleReset = async (u) => {
    if (await confirm({ text: `Reset password for ${u.name}?`, confirmText: 'Reset' })) resetPwMut.mutate(u.id)
  }

  const columns = [
    { key: 'name', header: 'Name' },
    { key: 'email', header: 'Email' },
    { key: 'role', header: 'Role', render: (u) => (
      <div className="d-flex flex-wrap gap-1">
        <span className="badge text-bg-primary">{roleLabel(u.accessType || u.role)}</span>
      </div>
    ) },
    { key: 'active', header: 'Status', render: (u) => <StatusBadge status={u.active ? 'success' : 'secondary'} label={u.active ? 'Active' : 'Inactive'} /> },
    { key: 'actions', header: 'Actions', render: (u) => (
      <div className="d-flex gap-1">
        <button className="btn btn-sm btn-light" title="Edit" onClick={() => openForm(u)} {...writeLock}><FiEdit2 /></button>
        <button className="btn btn-sm btn-light" title="Reset password" onClick={() => handleReset(u)} {...writeLock}><FiKey /></button>
        <button className="btn btn-sm btn-light text-danger" title="Remove" onClick={() => handleDelete(u)} {...writeLock}><FiTrash2 /></button>
      </div>
    ) },
  ]

  return (
    <>
      <PageHeader
        title="Users"
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Users' }]}
        actions={<Button write icon={FiPlus} onClick={() => openForm({})}>Add User</Button>}
      />
      <Card title={`Team (${data?.length ?? 0})`}>
        <DataTable columns={columns} rows={data ?? []} loading={isLoading} />
      </Card>

      <Modal show={editing !== null} title={editing?.id ? 'Edit User' : 'Add User'} onClose={() => setEditing(null)}>
        {editing !== null && (
          <UserForm defaultValues={editing} submitting={saveMut.isPending} error={saveMut.error}
            onSubmit={(v) => saveMut.mutate({ ...editing, ...v, __originalActive: editing.active })}
            onCancel={() => setEditing(null)} />
        )}
      </Modal>
    </>
  )
}

// "User limit reached for your plan (50)" — the backend's 400 on POST /users.
const isUserLimit = (msg) => /user limit reached/i.test(msg || '')

function UserForm({ defaultValues, onSubmit, onCancel, submitting, error }) {
  const isEdit = !!defaultValues?.id
  // Access types are the fixed backend enum.
  const currentAccess = defaultValues?.accessType || defaultValues?.role || ROLES.VIEWER
  const { register, handleSubmit, formState: { errors } } = useForm({
    defaultValues: { active: true, ...defaultValues, accessType: currentAccess, password: '' },
  })

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="row g-3">
      <div className="col-md-6">
        <label className="form-label">Name</label>
        <input className="form-control" {...register('name', { required: true })} />
      </div>
      <div className="col-md-6">
        <label className="form-label">Email</label>
        <input type="email" className="form-control" readOnly={isEdit}
          {...register('email', { required: !isEdit })} />
        {isEdit && <div className="form-text">Email can’t be changed.</div>}
      </div>
      <div className="col-md-6">
        <label className="form-label">Phone</label>
        <input className="form-control" {...register('phone')} />
      </div>
      <div className="col-md-6">
        <label className="form-label">Access Type</label>
        <select className="form-select" {...register('accessType')}>
          {ASSIGNABLE_ROLES.map((name) => <option key={name} value={name}>{ROLE_LABELS[name]}</option>)}
        </select>
      </div>
      {!isEdit && (
        <div className="col-md-6">
          <label className="form-label">Password</label>
          <input type="password" className={`form-control ${errors.password ? 'is-invalid' : ''}`}
            placeholder="Set a temporary password"
            {...register('password', { required: 'Password is required for new users', minLength: { value: 8, message: 'Min 8 characters' } })} />
          {errors.password && <div className="invalid-feedback">{errors.password.message}</div>}
        </div>
      )}
      <div className="col-md-6 d-flex align-items-end">
        <div className="form-check">
          <input type="checkbox" className="form-check-input" id="active" {...register('active')} />
          <label className="form-check-label" htmlFor="active">Active</label>
        </div>
      </div>
      {error && (
        <div className="col-12">
          <div className="alert alert-danger small mb-0">
            <div className="fw-semibold">{error.message || 'Could not save the user.'}</div>
            {isUserLimit(error.message) && (
              <div className="mt-1">
                Deactivated users still use a seat — delete unused accounts or upgrade your plan.{' '}
                <Link to="/my-subscription" className="fw-semibold">Upgrade</Link>
              </div>
            )}
          </div>
        </div>
      )}
      <div className="col-12 d-flex justify-content-end gap-2">
        <Button variant="light" type="button" onClick={onCancel}>Cancel</Button>
        <Button write type="submit" loading={submitting}>Save</Button>
      </div>
    </form>
  )
}
