"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"

import { AppSidebar } from "@/components/app-sidebar"
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar"
import { SpendRangePicker } from "@/components/spend-range-picker"
import { SpendRangeProvider } from "@/features/spend/range-context"
import { clearToken, isTokenValid } from "@/lib/auth"

export default function ConsoleLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const [authed, setAuthed] = useState(false)

  useEffect(() => {
    if (isTokenValid()) {
      setAuthed(true)
    } else {
      clearToken()
      router.replace("/login")
    }
  }, [router])

  if (!authed) return null

  return (
    <SpendRangeProvider>
      <SidebarProvider>
        <AppSidebar />
        <SidebarInset>
          <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
            <SidebarTrigger className="-ml-1" />
            <SpendRangePicker />
          </header>
          <div className="flex flex-1 flex-col gap-6 p-6">{children}</div>
        </SidebarInset>
      </SidebarProvider>
    </SpendRangeProvider>
  )
}
