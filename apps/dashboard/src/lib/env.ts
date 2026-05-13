export const env = {
  apiBaseUrl: import.meta.env.VITE_TANSO_BACKEND_URL || 'http://localhost:8080',
  sandboxApiBaseUrl: import.meta.env.VITE_TANSO_SANDBOX_BACKEND_URL || '',
  environment: (import.meta.env.VITE_ENVIRONMENT || 'local') as 'local' | 'staging' | 'sandbox' | 'production'
}
