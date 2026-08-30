"use client"

import { useState } from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import {
  Activity,
  Bell,
  ChevronRight,
  Coins,
  Gauge,
  GitMerge,
  LogOut,
  Package,
  Plug,
  Puzzle,
  ReceiptText,
  Repeat,
  Scale,
  Settings,
  Users,
  Wallet,
  Landmark,
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
  SidebarSeparator,
  useSidebar,
} from "@/components/ui/sidebar"
import { clearToken, getClaims } from "@/lib/auth"
import { isBuildSideOff, useVendorConnections } from "@/features/spend/queries"
import { usePortfolio } from "@/features/analytics/queries"
import { isModuleOff } from "@/lib/api/client"
import { cn } from "@/lib/utils"

// Internal spend: the AI you buy. Connections first, since every other page
// here is an empty shell until a vendor is connected — and it stays first
// afterwards, because that is where a connection's ERROR status surfaces.
const internalSpendNav = [
  { title: "Connections", href: "/spend/connections", icon: Plug },
  { title: "Usage", href: "/spend/usage", icon: Wallet },
  { title: "Teams", href: "/spend/teams", icon: Users },
  { title: "Alerts", href: "/spend/alerts", icon: Bell },
  { title: "Reconcile", href: "/spend/reconcile", icon: Scale },
  { title: "Outcomes", href: "/spend/outcomes", icon: GitMerge },
  { title: "Feature P&L", href: "/spend/pnl", icon: Landmark },
]

// Monetization: the AI you sell. Plans, credits, margin per customer.
const monetizationNav = [
  { title: "Plans", href: "/plans", icon: Package },
  { title: "Features", href: "/features", icon: Puzzle },
  { title: "Customers", href: "/customers", icon: Users },
  { title: "Subscriptions", href: "/subscriptions", icon: Repeat },
  { title: "Credits", href: "/credits", icon: Coins },
  { title: "Invoices", href: "/invoices", icon: ReceiptText },
  { title: "Events", href: "/events", icon: Activity },
]

// Which groups the operator left open, remembered across visits. Reading
// localStorage in the initializer is safe here: the console layout renders
// nothing until it has checked for a token, so the sidebar only ever mounts
// on the client.
function useGroupOpen(key: string, defaultOpen: boolean) {
  const storageKey = `tanso.sidebar.${key}`
  const [open, setOpen] = useState(() => {
    const stored = localStorage.getItem(storageKey)
    return stored === null ? defaultOpen : stored === "true"
  })

  function toggle() {
    setOpen((current) => {
      localStorage.setItem(storageKey, String(!current))
      return !current
    })
  }

  return [open, toggle] as const
}

export function AppSidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const { isMobile, setOpenMobile } = useSidebar()
  const email = getClaims()?.email
  const vendorConnections = useVendorConnections()
  const buildSideOff = isBuildSideOff(vendorConnections.error)
  const portfolio = usePortfolio()
  const monetizationOff = isModuleOff(portfolio.error)

  const [spendStored, toggleSpend] = useGroupOpen("internal-spend", true)
  // Collapsed by default: internal spend is the front door. When the build
  // side is off there is nothing else to show, so monetization opens instead.
  const [monetizationStored, toggleMonetization] = useGroupOpen(
    "monetization",
    buildSideOff
  )

  // A collapsed group must still show the page you are on, or the active item
  // is invisible while you are standing on it.
  const inSpend = pathname.startsWith("/spend")
  const spendOpen = spendStored || inSpend
  const monetizationOpen =
    monetizationStored || (!inSpend && pathname !== "/" && !isActive("/settings"))

  // On mobile the sidebar is a sheet; a tap on a link should close it.
  function closeMobile() {
    if (isMobile) setOpenMobile(false)
  }

  function isActive(href: string) {
    return href === "/" ? pathname === "/" : pathname.startsWith(href)
  }

  function logout() {
    clearToken()
    router.replace("/login")
  }

  function renderItems(items: typeof internalSpendNav) {
    return items.map((item) => (
      <SidebarMenuItem key={item.href}>
        <SidebarMenuButton
          render={<Link href={item.href} />}
          isActive={isActive(item.href)}
          onClick={closeMobile}
        >
          <item.icon />
          {item.title}
        </SidebarMenuButton>
      </SidebarMenuItem>
    ))
  }

  function renderGroupLabel(label: string, open: boolean, toggle: () => void) {
    return (
      <SidebarGroupLabel
        render={<button type="button" onClick={toggle} />}
        className="w-full cursor-pointer justify-between hover:text-sidebar-foreground"
        aria-expanded={open}
      >
        {label}
        <ChevronRight
          className={cn("transition-transform duration-200", open && "rotate-90")}
        />
      </SidebarGroupLabel>
    )
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
                  onClick={closeMobile}
                >
                  <Gauge />
                  Overview
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
        {!buildSideOff && (
          <SidebarGroup>
            {renderGroupLabel("Internal spend", spendOpen, toggleSpend)}
            {spendOpen && (
              <SidebarGroupContent>
                <SidebarMenu>
                  {renderItems(
                    monetizationOff
                      ? internalSpendNav.filter((i) => i.href !== "/spend/pnl")
                      : internalSpendNav
                  )}
                </SidebarMenu>
              </SidebarGroupContent>
            )}
          </SidebarGroup>
        )}
        {!monetizationOff && (
          <>
            {!buildSideOff && <SidebarSeparator />}
            <SidebarGroup>
              {renderGroupLabel(
                "Monetization",
                monetizationOpen,
                toggleMonetization
              )}
              {monetizationOpen && (
                <SidebarGroupContent>
                  <SidebarMenu>{renderItems(monetizationNav)}</SidebarMenu>
                </SidebarGroupContent>
              )}
            </SidebarGroup>
          </>
        )}
      </SidebarContent>
      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              render={<Link href="/settings" />}
              isActive={isActive("/settings")}
              onClick={closeMobile}
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
