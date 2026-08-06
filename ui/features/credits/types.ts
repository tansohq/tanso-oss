export interface CreditFeatureWeightDto {
  id: string
  featureId: string
  featureKey: string
  model: string | null
  creditsPerUnit: number
  effectiveFrom: string
  createdBy: string | null
  createdAt: string
}

export interface PublishCreditWeightsInput {
  effectiveFrom: string
  entries: {
    featureId: string
    model?: string
    creditsPerUnit: number
  }[]
}

export interface CreditPriceDto {
  id: string
  denomination: string
  currency: string
  pricePerCredit: number
  effectiveFrom: string
  createdBy: string | null
  createdAt: string
}

export interface PublishCreditPricesInput {
  effectiveFrom: string
  entries: {
    denomination: string
    currency?: string
    pricePerCredit: number
  }[]
}
