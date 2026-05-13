export function useTracking() {
  return {
    track: (_event: string, _properties?: Record<string, unknown>) => {}
  }
}
