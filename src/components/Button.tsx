import type { ButtonHTMLAttributes } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'danger-soft'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  small?: boolean
}

export function Button({ variant = 'secondary', small = false, className = '', type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={`btn btn-${variant}${small ? ' btn-small' : ''} ${className}`.trim()}
      {...props}
    />
  )
}
