// Hand-written on purpose, like features/spend/types.ts: keep in step with
// model/analytics/AgentFunnelResponse.java. New top-level fields are snake_case
// per the agent onboarding v2 contract.
export interface AgentFunnelDayDto {
  date: string
  signups: number
  first_verified_job: number
  claimed: number
  paid: number
}

export interface AgentFunnelDto {
  period: { from: string; to: string }
  stages: {
    signups: number
    first_verified_job: number
    claimed: number
    paid: number
  }
  rates: {
    activation: number
    claim: number
    paid: number
  }
  median_hours_signup_to_first_job: number | null
  median_hours_signup_to_paid: number | null
  expired: number
  by_day: AgentFunnelDayDto[]
}
