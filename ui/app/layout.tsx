import { Geist, Geist_Mono } from "next/font/google"

import type { Metadata } from "next"

import "./globals.css"
import { Providers } from "@/components/providers"
import { ThemeProvider } from "@/components/theme-provider"
import { Toaster } from "@/components/ui/toast"
import { cn } from "@/lib/utils";

export const metadata: Metadata = {
  title: "Tanso Console",
  description: "Admin console for the Tanso monetization engine",
}

const geist = Geist({subsets:['latin'],variable:'--font-sans'})

const fontMono = Geist_Mono({
  subsets: ["latin"],
  variable: "--font-mono",
})

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={cn("antialiased", fontMono.variable, "font-sans", geist.variable)}
    >
      <body>
        <ThemeProvider>
          <Providers>
            <Toaster>{children}</Toaster>
          </Providers>
        </ThemeProvider>
      </body>
    </html>
  )
}
