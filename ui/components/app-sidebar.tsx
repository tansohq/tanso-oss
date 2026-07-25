"use client"

import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import {
  Activity,
  Coins,
  Gauge,
  LogOut,
  Package,
  Puzzle,
  ReceiptText,
  Repeat,
  Settings,
  Users,
} from "lucide-react"

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import { clearToken, getClaims } from "@/lib/auth"

const catalogNav = [
  { title: "Plans", href: "/plans", icon: Package },
  { title: "Features", href: "/features", icon: Puzzle },
]

const customersNav = [
  { title: "Customers", href: "/customers", icon: Users },
  { title: "Subscriptions", href: "/subscriptions", icon: Repeat },
  { title: "Credits", href: "/credits", icon: Coins },
  { title: "Invoices", href: "/invoices", icon: ReceiptText },
]

const usageNav = [{ title: "Events", href: "/events", icon: Activity }]

export function AppSidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const email = getClaims()?.email

  function isActive(href: string) {
    return href === "/" ? pathname === "/" : pathname.startsWith(href)
  }

  function logout() {
    clearToken()
    router.replace("/login")
  }

  return (
    <Sidebar>
      <SidebarHeader>
        <div className="flex items-baseline gap-2 px-2 py-1">
          <span className="text-lg font-semibold tracking-tight">tanso</span>
          <span className="font-mono text-xs text-primary">console</span>
        </div>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton
                  render={<Link href="/" />}
                  isActive={isActive("/")}
                >
                  <Gauge />
                  Overview
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
        <SidebarGroup>
          <SidebarGroupLabel>Catalog</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {catalogNav.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton
                    render={<Link href={item.href} />}
                    isActive={isActive(item.href)}
                  >
                    <item.icon />
                    {item.title}
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
        <SidebarGroup>
          <SidebarGroupLabel>Customers</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {customersNav.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton
                    render={<Link href={item.href} />}
                    isActive={isActive(item.href)}
                  >
                    <item.icon />
                    {item.title}
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
        <SidebarGroup>
          <SidebarGroupLabel>Usage</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {usageNav.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton
                    render={<Link href={item.href} />}
                    isActive={isActive(item.href)}
                  >
                    <item.icon />
                    {item.title}
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              render={<Link href="/settings" />}
              isActive={isActive("/settings")}
            >
              <Settings />
              Settings
            </SidebarMenuButton>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <SidebarMenuButton onClick={logout}>
              <LogOut />
              <span className="truncate">{email ? `Sign out (${email})` : "Sign out"}</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  )
}
