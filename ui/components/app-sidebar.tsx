"use client"

import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import {
  Activity,
  Bell,
  Coins,
  Gauge,
  GitMerge,
  LogOut,
  Package,
  Puzzle,
  ReceiptText,
  Repeat,
  Scale,
  Settings,
  Users,
  Wallet,
  PiggyBank,
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
import { isBuildSideOff, useVendorConnections } from "@/features/spend/queries"

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

const spendNav = [
  { title: "Usage", href: "/spend/usage", icon: Gauge },
  { title: "Teams", href: "/spend/teams", icon: Users },
  { title: "Alerts", href: "/spend/alerts", icon: Bell },
  { title: "Outcomes", href: "/spend/outcomes", icon: GitMerge },
  { title: "Savings", href: "/spend/savings", icon: PiggyBank },
  { title: "Reconcile", href: "/spend/reconcile", icon: Scale },
  { title: "Connections", href: "/spend/connections", icon: Wallet },
]

export function AppSidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const email = getClaims()?.email
  const vendorConnections = useVendorConnections()
  const buildSideOff = isBuildSideOff(vendorConnections.error)

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
        <div className="flex items-center gap-2 px-2 py-1">
          <div className="flex items-baseline gap-2">
            <span className="text-lg font-semibold tracking-tight">Tanso</span>
            <span className="font-mono text-xs text-primary">console</span>
          </div>
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
        {!buildSideOff && (
          <SidebarGroup>
            <SidebarGroupLabel>Spend</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {spendNav.map((item) => (
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
        )}
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
              Sign out
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        {email && (
          <div className="truncate px-2 pb-1 text-xs text-muted-foreground">
            {email}
          </div>
        )}
      </SidebarFooter>
    </Sidebar>
  )
}
