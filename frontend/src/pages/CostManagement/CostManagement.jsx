import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { FiSave, FiInfo } from 'react-icons/fi'
import { useAuth } from '../../context/AuthContext'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import CostFields from '../../components/forms/CostFields'
import { costModelService } from '../../services/costModelService'
import { useNotification } from '../../context/NotificationContext'

// GET / PUT /cost-model — the tenant's GLOBAL cost defaults (FR-COST-001).
// Identical in shape to a product's own cost profile, so a product with no
// profile of its own inherits these wholesale (costSource: TENANT_DEFAULT).

export default function CostManagement() {
  // Viewable while the plan is expired; nothing here can be changed until it is renewed.
  const { readOnly } = useAuth()
  const qc = useQueryClient()
  const { notify } = useNotification()
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['costModel'], queryFn: costModelService.get,
  })
  const { register, handleSubmit, reset } = useForm({ defaultValues: { currency: 'USD' } })

  // configured === false means the tenant has never saved — leave the form
  // empty rather than pre-filling zeros that look like real decisions.
  useEffect(() => {
    if (data) reset(data.configured ? data : { currency: data.currency || 'USD' })
  }, [data, reset])

  const save = useMutation({
    mutationFn: (values) => costModelService.save(values),
    onSuccess: () => {
      notify.success('Cost model saved')
      qc.invalidateQueries({ queryKey: ['costModel'] })
    },
    onError: (err) => notify.error(err?.message || 'Could not save the cost model.'),
  })

  if (isLoading) return <Loader />

  return (
    <>
      <PageHeader title="Cost Management"
        subtitle="Default costs applied to every product unless the product overrides them."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Cost Management' }]} />

      {isError && (
        <div className="alert alert-danger py-2 small">{error?.message || 'Could not load the cost model.'}</div>
      )}

      <div className="row g-3">
        <div className="col-12 col-xl-8">
          <Card title="Cost model">
            {data && !data.configured && (
              <div className="alert alert-warning d-flex align-items-center gap-2 py-2 small">
                <FiInfo className="flex-shrink-0" />
                No costs saved yet — margin, ROI and break-even stay unavailable until you add them.
              </div>
            )}
            <form onSubmit={handleSubmit((v) => save.mutate(v))} className="row g-3">
              <CostFields register={register} />
              <div className="col-12 d-flex justify-content-end">
                <Button type="submit" icon={FiSave} loading={save.isPending} disabled={readOnly} title={readOnly ? 'Renew to use this' : undefined}>Save costs</Button>
              </div>
            </form>
          </Card>
        </div>

        <div className="col-12 col-xl-4">
          <Card title="How these are used">
            <p className="text-muted small mb-2">
              Every profit figure — contribution margin, ROI, break-even and the price
              recommendations — comes from these costs plus the product&apos;s selling price.
            </p>
            <div className="form-label mt-3 mb-2">Which costs apply</div>
            <ol className="text-muted small ps-3 mb-2">
              <li>The product&apos;s own cost profile, if it has one.</li>
              <li>Otherwise these global defaults.</li>
              <li>Otherwise profit stays unavailable.</li>
            </ol>
            <p className="text-muted small mb-0">
              A product&apos;s profile replaces these entirely rather than merging field by field,
              so a product can deliberately set a cost to zero.
            </p>
          </Card>
        </div>
      </div>
    </>
  )
}
