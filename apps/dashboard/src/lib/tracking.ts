export function useTracking() {
  return {
    track: (_event: string, _properties?: Record<string, unknown>) => {},
    identify: (_userId: string, _properties?: Record<string, unknown>) => {}
  }
}
