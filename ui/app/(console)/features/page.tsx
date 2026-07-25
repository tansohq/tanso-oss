"use client"

import { useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import { Plus } from "lucide-react"

import { DataTable } from "@/components/data-table"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { toast } from "@/components/ui/toast"
import type { FeatureDto } from "@/lib/api/types"
import { formatDate } from "@/lib/format"
import { useCreateFeature, useUpdateFeature } from "@/features/features/mutations"
import { FeatureForm } from "@/features/features/feature-form"
import { useFeatures } from "@/features/features/queries"

const columns: ColumnDef<FeatureDto>[] = [
  { accessorKey: "name", header: "Name" },
  {
    accessorKey: "key",
    header: "Key",
    cell: ({ row }) => <span className="font-mono text-xs">{row.original.key}</span>,
  },
  {
    accessorKey: "isEnabled",
    header: "Status",
    cell: ({ row }) => (
      <Badge variant={row.original.isEnabled ? "default" : "secondary"}>
        {row.original.isEnabled ? "Enabled" : "Disabled"}
      </Badge>
    ),
  },
  {
    accessorKey: "description",
    header: "Description",
    cell: ({ row }) => (
      <span className="block max-w-md truncate text-muted-foreground">
        {row.original.description}
      </span>
    ),
  },
  {
    accessorKey: "createdAt",
    header: "Created",
    cell: ({ row }) => formatDate(row.original.createdAt),
  },
]

export default function FeaturesPage() {
  const features = useFeatures()
  const createFeature = useCreateFeature()
  const [createOpen, setCreateOpen] = useState(false)
  const [editFeature, setEditFeature] = useState<FeatureDto | null>(null)
  const updateFeature = useUpdateFeature(editFeature?.id ?? "")

  return (
    <>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Features</h1>
          <p className="text-sm text-muted-foreground">
            Capabilities you can attach to plans with pricing rules.
          </p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus data-icon="inline-start" />
          New feature
        </Button>
      </div>
      <DataTable
        columns={columns}
        data={features.data ?? []}
        isLoading={features.isPending}
        emptyTitle="No features yet"
        emptyDescription="Create a feature, then attach it to a plan."
        onRowClick={(feature) => setEditFeature(feature)}
      />
      <Sheet open={createOpen} onOpenChange={setCreateOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>New feature</SheetTitle>
            <SheetDescription>A single capability customers can be entitled to.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <FeatureForm
              isPending={createFeature.isPending}
              onSubmit={(input) =>
                createFeature.mutate(input, {
                  onSuccess: () => {
                    setCreateOpen(false)
                    toast.add({ title: "Feature created" })
                  },
                  onError: (error) =>
                    toast.add({ title: "Create failed", description: error.message }),
                })
              }
            />
          </div>
        </SheetContent>
      </Sheet>
      <Sheet open={!!editFeature} onOpenChange={(open) => !open && setEditFeature(null)}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Edit feature</SheetTitle>
            <SheetDescription>Changes apply everywhere this feature is used.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            {editFeature && (
              <FeatureForm
                feature={editFeature}
                isPending={updateFeature.isPending}
                onSubmit={(input) =>
                  updateFeature.mutate(input, {
                    onSuccess: () => {
                      setEditFeature(null)
                      toast.add({ title: "Feature updated" })
                    },
                    onError: (error) =>
                      toast.add({ title: "Update failed", description: error.message }),
                  })
                }
              />
            )}
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}
