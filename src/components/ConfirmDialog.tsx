import { AlertTriangle } from 'lucide-react'
import { Button } from './Button'
import { Modal } from './Modal'

interface ConfirmDialogProps {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  onConfirm: () => void
  onClose: () => void
}

export function ConfirmDialog({ open, title, message, confirmLabel, danger = true, onConfirm, onClose }: ConfirmDialogProps) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <div className="confirm-body">
        <span className={`confirm-icon${danger ? ' is-danger' : ''}`}><AlertTriangle size={25} /></span>
        <p>{message}</p>
      </div>
      <footer className="modal-footer">
        <Button onClick={onClose}>انصراف</Button>
        <Button variant={danger ? 'danger' : 'primary'} onClick={() => { onConfirm(); onClose() }}>{confirmLabel}</Button>
      </footer>
    </Modal>
  )
}
