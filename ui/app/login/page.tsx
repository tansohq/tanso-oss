"use client"

import { useRouter } from "next/navigation"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { ApiError } from "@/lib/api/client"
import { useLogin } from "@/features/auth/mutations"
import { loginSchema, type LoginInput } from "@/features/auth/schemas"

export default function LoginPage() {
  const router = useRouter()
  const login = useLogin()
  const form = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" },
  })

  function onSubmit(input: LoginInput) {
    login.mutate(input, { onSuccess: () => router.replace("/") })
  }

  const submitError =
    login.error instanceof ApiError && login.error.status !== 401
      ? login.error.message
      : login.error
        ? "Wrong email or password"
        : null

  return (
    <main className="flex min-h-svh items-center justify-center p-6">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex items-center justify-center gap-3">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo.svg" alt="" className="size-10 rounded-lg" />
          <div className="flex items-baseline gap-2">
            <span className="text-2xl font-semibold tracking-tight">Tanso</span>
            <span className="font-mono text-sm text-primary">console</span>
          </div>
        </div>
        <Card>
          <CardHeader>
            <CardTitle>Sign in</CardTitle>
            <CardDescription>Use your Tanso account credentials.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={form.handleSubmit(onSubmit)}>
              <FieldGroup>
                <Field data-invalid={!!form.formState.errors.username || undefined}>
                  <FieldLabel htmlFor="username">Email or username</FieldLabel>
                  <Input
                    id="username"
                    autoComplete="username"
                    aria-invalid={!!form.formState.errors.username}
                    {...form.register("username")}
                  />
                  {form.formState.errors.username && (
                    <FieldError>{form.formState.errors.username.message}</FieldError>
                  )}
                </Field>
                <Field data-invalid={!!form.formState.errors.password || undefined}>
                  <FieldLabel htmlFor="password">Password</FieldLabel>
                  <Input
                    id="password"
                    type="password"
                    autoComplete="current-password"
                    aria-invalid={!!form.formState.errors.password}
                    {...form.register("password")}
                  />
                  {form.formState.errors.password && (
                    <FieldError>{form.formState.errors.password.message}</FieldError>
                  )}
                </Field>
                {submitError && <FieldError>{submitError}</FieldError>}
                <Button type="submit" disabled={login.isPending} className="w-full">
                  {login.isPending && <Spinner data-icon="inline-start" />}
                  Sign in
                </Button>
              </FieldGroup>
            </form>
          </CardContent>
        </Card>
      </div>
    </main>
  )
}
