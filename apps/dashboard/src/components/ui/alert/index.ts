import type { VariantProps } from 'class-variance-authority'
import { cva } from 'class-variance-authority'

export { default as Alert } from './Alert.vue'
export { default as AlertDescription } from './AlertDescription.vue'
export { default as AlertTitle } from './AlertTitle.vue'

export const alertVariants = cva(
  'relative w-full rounded-lg border px-4 py-3 text-sm flex items-center gap-3 [&>svg]:flex-shrink-0 [&>svg]:text-foreground',
  {
    variants: {
      variant: {
        default: 'bg-background text-foreground',
        destructive:
          'border-destructive/50 text-destructive dark:border-destructive [&>svg]:text-destructive',
        warning:
          'border-amber-200 bg-amber-50/50 text-amber-900 dark:border-amber-500/50 dark:bg-amber-900/20 dark:text-amber-100 [&>svg]:text-amber-600 dark:[&>svg]:text-amber-400'
      }
    },
    defaultVariants: {
      variant: 'default'
    }
  }
)

export type AlertVariants = VariantProps<typeof alertVariants>
