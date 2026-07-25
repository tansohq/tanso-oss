import { useMutation } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { JwtResponse } from "@/lib/api/types"
import { setToken } from "@/lib/auth"
import type { LoginInput } from "./schemas"

export function useLogin() {
  return useMutation({
    mutationFn: (input: LoginInput) =>
      apiFetch<JwtResponse>("/public/v1/login", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: (data) => {
      if (data.token) setToken(data.token)
    },
  })
}
