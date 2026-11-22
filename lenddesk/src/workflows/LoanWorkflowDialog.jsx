import { lazy, Suspense, useEffect, useId, useRef, useState } from 'react'
import './LoanWorkflowDialog.css'

const WorkflowPage = lazy(() => import('./WorkflowPage'))
const titles = {
  loan: 'Manage loan',
  margin: 'Issue margin call',
  recall: 'Create recall',
  return: 'Manage return request',
}

export default function LoanWorkflowDialog({ kind, id, onDismiss, onComplete }) {
  const dialog = useRef(null)
  const titleId = useId()
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const element = dialog.current
    const trigger = document.activeElement
    element.showModal()
    return () => {
      element.close()
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus()
    }
  }, [])

  function complete(message) {
    onDismiss()
    onComplete?.(message)
  }

  return (
    <dialog
      ref={dialog}
      className="loan-workflow-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        if (!busy) onDismiss()
      }}
    >
      <header className="loan-workflow-dialog-bar">
        <h2 id={titleId}>{titles[kind]}</h2>
        <button type="button" disabled={busy} onClick={onDismiss}>Close</button>
      </header>
      <Suspense fallback={<p role="status">Loading workflow…</p>}>
        <WorkflowPage
          kind={kind}
          recordId={id}
          onDismiss={onDismiss}
          onComplete={complete}
          onBusyChange={setBusy}
        />
      </Suspense>
    </dialog>
  )
}
