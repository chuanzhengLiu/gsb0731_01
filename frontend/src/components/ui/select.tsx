import * as React from 'react'
import { cn } from '@/lib/utils'
import { ChevronDown } from 'lucide-react'

interface SelectProps {
  value: string
  onValueChange: (value: string) => void
  children: React.ReactNode
  disabled?: boolean
}

const SelectContext = React.createContext<{
  value: string
  onValueChange: (value: string) => void
  disabled?: boolean
} | null>(null)

const Select: React.FC<SelectProps> = ({ value, onValueChange, children, disabled }) => {
  return (
    <SelectContext.Provider value={{ value, onValueChange, disabled }}>
      <div className="relative">{children}</div>
    </SelectContext.Provider>
  )
}

const SelectTrigger = React.forwardRef<HTMLButtonElement, React.ButtonHTMLAttributes<HTMLButtonElement>>(
  ({ className, children, disabled: triggerDisabled, ...props }, ref) => {
    const ctx = React.useContext(SelectContext)
    const [open, setOpen] = React.useState(false)
    const isDisabled = triggerDisabled || ctx?.disabled

    React.useEffect(() => {
      const handler = () => setOpen(false)
      if (open) {
        document.addEventListener('click', handler)
        return () => document.removeEventListener('click', handler)
      }
    }, [open])

    return (
      <>
        <button
          ref={ref}
          type="button"
          disabled={isDisabled}
          className={cn(
            'flex h-10 w-full items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
            className
          )}
          onClick={(e) => {
            e.stopPropagation()
            if (!isDisabled) setOpen(!open)
          }}
          {...props}
        >
          {children}
          <ChevronDown className="h-4 w-4 opacity-50" />
        </button>
        {open && (
          <div
            className="absolute left-0 right-0 top-full z-50 mt-1 rounded-md border bg-popover p-1 shadow-md max-h-60 overflow-auto"
            onClick={(e) => e.stopPropagation()}
          >
            {React.Children.map(children, (child) => {
              if (React.isValidElement(child) && child.type === SelectContent) {
                return React.cloneElement(child as React.ReactElement<any>, {
                  onClose: () => setOpen(false),
                })
              }
              return null
            })}
          </div>
        )}
      </>
    )
  }
)
SelectTrigger.displayName = 'SelectTrigger'

const SelectValue: React.FC<{ placeholder?: string }> = ({ placeholder }) => {
  const ctx = React.useContext(SelectContext)
  return <span className={ctx?.value ? '' : 'text-muted-foreground'}>{ctx?.value || placeholder}</span>
}

interface SelectContentProps extends React.HTMLAttributes<HTMLDivElement> {
  onClose?: () => void
}

const SelectContent = React.forwardRef<HTMLDivElement, SelectContentProps>(
  ({ className, children, onClose, ...props }, ref) => {
    const ctx = React.useContext(SelectContext)
    return (
      <div ref={ref} className={cn('', className)} {...props}>
        {React.Children.map(children, (child) => {
          if (React.isValidElement(child) && child.type === SelectItem) {
            return React.cloneElement(child as React.ReactElement<any>, {
              selected: (child as any).props.value === ctx?.value,
              onSelect: () => {
                ctx?.onValueChange((child as any).props.value)
                onClose?.()
              },
            })
          }
          return child
        })}
      </div>
    )
  }
)
SelectContent.displayName = 'SelectContent'

interface SelectItemProps {
  value: string
  children: React.ReactNode
  selected?: boolean
  disabled?: boolean
  onSelect?: () => void
}

const SelectItem: React.FC<SelectItemProps> = ({ value, children, selected, disabled, onSelect }) => (
  <div
    className={cn(
      'relative flex w-full cursor-pointer select-none items-center rounded-sm py-1.5 px-2 text-sm outline-none hover:bg-accent',
      selected && 'bg-accent',
      disabled && 'opacity-50 cursor-not-allowed'
    )}
    onClick={() => {
      if (!disabled) onSelect?.()
    }}
  >
    {children}
  </div>
)

export { Select, SelectTrigger, SelectValue, SelectContent, SelectItem }
