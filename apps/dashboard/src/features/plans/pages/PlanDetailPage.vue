<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="mb-6">
      <nav class="flex items-center gap-2 text-sm text-muted-foreground">
        <router-link :to="demoPath('/plans')" class="hover:text-foreground transition-colors">Plans</router-link>
        <ChevronRight class="h-4 w-4" />
        <span class="text-foreground">{{ plan?.name || 'Plan' }}</span>
      </nav>
    </div>

    <!-- Loading State -->
    <div v-if="isLoading" class="flex items-center justify-center py-12">
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <!-- Error State -->
    <div v-else-if="isError" class="flex flex-col items-center justify-center py-12 bg-card rounded-lg border">
      <AlertCircle class="w-12 h-12 text-destructive mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">Unable to load plan</p>
      <p class="text-sm text-muted-foreground mb-4">The plan could not be found or an error occurred.</p>
      <div class="flex gap-2">
        <Button variant="outline" @click="refetch">
          <RefreshCw class="w-4 h-4 mr-2" />
          Try Again
        </Button>
        <Button variant="outline" @click="router.push(demoPath('/plans'))">
          <ArrowLeft class="w-4 h-4 mr-2" />
          Back to Plans
        </Button>
      </div>
    </div>

    <div v-else-if="plan" class="space-y-6 max-w-5xl">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-semibold tracking-tight text-foreground">{{ plan.name }}</h1>
          <div class="flex items-center gap-2 mt-1">
            <p class="text-sm font-mono text-muted-foreground">{{ plan.key }}</p>
            <CopyButton :value="plan.key" label="Plan Key" />
          </div>
        </div>
        <div class="flex items-center gap-2">
          <!-- Draft: Activate button + overflow menu -->
          <template v-if="planStatus === 'draft'">
            <Button
              size="sm"
              class="bg-green-600 hover:bg-green-700 text-white"
              :disabled="isActivating"
              @click="handleActivateClick"
            >
              <Loader2 v-if="isActivating" class="h-3.5 w-3.5 animate-spin mr-1.5" />
              Activate Plan
            </Button>
          </template>

          <!-- Active: overflow menu -->
          <template v-if="planStatus === 'active'">
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button variant="ghost" size="sm" class="h-8 w-8 p-0" aria-label="Plan actions">
                  <MoreHorizontal class="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem @click="showArchiveConfirm = true" :disabled="isArchiving">
                  <Archive class="w-4 h-4 mr-2" />
                  Archive Plan
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </template>

          <!-- Archived: overflow menu -->
<!--          <template v-if="planStatus === 'archived'">
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button variant="ghost" size="sm" class="h-8 w-8 p-0" aria-label="Plan actions">
                  <MoreHorizontal class="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem @click="showRestoreConfirm = true" :disabled="isRestoring">
                  <RotateCcw class="w-4 h-4 mr-2" />
                  Restore Plan
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </template>-->
        </div>
      </div>

      <!-- Draft Banner -->
      <div
        v-if="planStatus === 'draft'"
        class="flex items-center gap-2 p-3 rounded-lg border border-amber-200 bg-amber-50"
      >
        <AlertTriangle class="h-4 w-4 text-amber-600 shrink-0" />
        <span class="text-sm text-amber-700"
          >This plan is in draft mode and not visible to customers. Configure pricing and features, then activate to make it available.</span
        >
      </div>

      <!-- Archived Banner -->
      <div
        v-if="planStatus === 'archived'"
        class="flex items-center gap-2 p-3 rounded-lg border border-gray-200 bg-gray-50"
      >
        <Archive class="h-4 w-4 text-gray-500 shrink-0" />
        <span class="text-sm text-gray-600">This plan is archived and read-only.</span>
      </div>

      <!-- Tab Bar -->
      <Tabs v-model="activeTab">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="subscriptions">Subscriptions</TabsTrigger>
        </TabsList>
      </Tabs>

      <!-- Overview Tab -->
      <div v-if="activeTab === 'overview'" class="space-y-6">
        <!-- Plan Info Card - View Mode -->
        <Card v-if="!isEditing" class="p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Plan Details</h3>
            <div class="flex items-center gap-2">
              <span v-if="justSaved" class="text-xs text-green-600">Saved</span>
              <Button
                v-if="planStatus !== 'archived'"
                variant="ghost"
                size="sm"
                class="text-xs h-7"
                @click="startEditing"
              >
                <Pencil class="w-3.5 h-3.5 mr-1" />
                Edit
              </Button>
            </div>
          </div>
          <div class="grid grid-cols-3 gap-6 text-sm">
            <div>
              <div class="text-muted-foreground mb-1">Status</div>
              <Badge
                :class="statusBadgeClass"
              >
                {{ planStatus.charAt(0).toUpperCase() + planStatus.slice(1) }}
              </Badge>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Billing Cycle</div>
              <div class="font-medium">{{ formatInterval(plan.intervalMonths) }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Key</div>
              <div class="font-mono">{{ plan.key }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Billing Timing</div>
              <div class="font-medium">{{ plan.billingTiming === 'IN_ARREARS' ? 'In arrears' : 'In advance' }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Subscriptions</div>
              <div class="font-medium">{{ planSubscriptions.length > 0 ? planSubscriptions.length : '\u2014' }}</div>
            </div>
          </div>

          <!-- Description -->
          <div v-if="plan.description" class="mt-6 pt-4 border-t">
            <div class="text-muted-foreground text-sm mb-1">Description</div>
            <div class="text-sm">{{ plan.description }}</div>
          </div>

          <!-- Custom Fields (collapsible, only if metadata exists) -->
          <Collapsible v-if="plan.metadata && Object.keys(plan.metadata).length > 0" v-model:open="showMoreDetails" class="mt-6 pt-4 border-t">
            <CollapsibleTrigger class="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors">
              <ChevronRight class="h-4 w-4 transition-transform" :class="{ 'rotate-90': showMoreDetails }" />
              Custom Fields
            </CollapsibleTrigger>
            <CollapsibleContent class="mt-4">
              <div class="flex flex-wrap gap-2">
                <div
                  v-for="(value, mkey) in plan.metadata"
                  :key="mkey"
                  class="inline-flex items-center gap-1.5 text-xs bg-muted px-2.5 py-1 rounded-md"
                >
                  <span class="text-muted-foreground">{{ mkey }}:</span>
                  <span class="font-medium">{{ value }}</span>
                </div>
              </div>
            </CollapsibleContent>
          </Collapsible>
        </Card>

        <!-- Plan Details Card - Edit Mode -->
        <Card v-else class="p-6">
          <h3 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-4">Plan Details</h3>
          <form @submit.prevent="onSubmit" class="space-y-4">
            <Alert v-if="errorMessage" variant="destructive">
              <AlertCircle class="h-4 w-4" />
              <AlertDescription>{{ errorMessage }}</AlertDescription>
            </Alert>

            <div class="grid grid-cols-2 gap-4">
              <div class="flex flex-col gap-2">
                <Label for="name" class="text-sm text-muted-foreground">Name *</Label>
                <Input
                  id="name"
                  v-model="formName"
                  :class="{ 'border-destructive': errors.name }"
                  placeholder="e.g., Basic Plan"
                />
                <span v-if="errors.name" class="text-destructive text-xs">{{ errors.name }}</span>
              </div>

              <div class="flex flex-col gap-2">
                <Label for="key" class="text-sm text-muted-foreground">
                  Key *
                  <Lock v-if="!canEditKey" class="inline h-3 w-3 ml-1 text-muted-foreground" />
                </Label>
                <TooltipProvider v-if="!canEditKey">
                  <Tooltip>
                    <TooltipTrigger as-child>
                      <Input
                        id="key"
                        :model-value="formKey"
                        disabled
                        class="opacity-60"
                      />
                    </TooltipTrigger>
                    <TooltipContent>Key cannot be changed after activation</TooltipContent>
                  </Tooltip>
                </TooltipProvider>
                <template v-else>
                  <Input
                    id="key"
                    v-model="formKey"
                    :class="{ 'border-destructive': errors.key }"
                    placeholder="e.g., basic_plan"
                  />
                  <span v-if="errors.key" class="text-destructive text-xs">{{ errors.key }}</span>
                  <span v-else class="text-xs text-muted-foreground">Locked once the plan is activated.</span>
                </template>
              </div>

              <div class="flex flex-col gap-2">
                <Label for="intervalMonths" class="text-sm text-muted-foreground">
                  Billing Interval *
                  <Lock v-if="!canEditInterval" class="inline h-3 w-3 ml-1 text-muted-foreground" />
                </Label>
                <TooltipProvider v-if="!canEditInterval">
                  <Tooltip>
                    <TooltipTrigger as-child>
                      <div>
                        <Select v-model="formIntervalMonths" disabled>
                          <SelectTrigger class="opacity-60">
                            <SelectValue placeholder="Select interval" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="1">Monthly</SelectItem>
                            <SelectItem value="3">Quarterly</SelectItem>
                            <SelectItem value="6">Semi-annual</SelectItem>
                            <SelectItem value="12">Annual</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                    </TooltipTrigger>
                    <TooltipContent>Billing interval cannot be changed after activation</TooltipContent>
                  </Tooltip>
                </TooltipProvider>
                <Select v-else v-model="formIntervalMonths">
                  <SelectTrigger :class="{ 'border-destructive': errors.intervalMonths }">
                    <SelectValue placeholder="Select interval" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="1">Monthly</SelectItem>
                    <SelectItem value="3">Quarterly</SelectItem>
                    <SelectItem value="6">Semi-annual</SelectItem>
                    <SelectItem value="12">Annual</SelectItem>
                  </SelectContent>
                </Select>
                <span v-if="errors.intervalMonths" class="text-destructive text-xs">{{ errors.intervalMonths }}</span>
              </div>

              <div class="flex flex-col gap-2 col-span-2">
                <Label for="description" class="text-sm text-muted-foreground">Description *</Label>
                <Textarea
                  id="description"
                  v-model="formDescription"
                  :class="{ 'border-destructive': errors.description }"
                  placeholder="Plan description"
                  rows="2"
                />
                <span v-if="errors.description" class="text-destructive text-xs">{{ errors.description }}</span>
              </div>

              <div class="col-span-2">
                <MetadataEditor v-model="metadata" />
              </div>
            </div>

            <div class="flex items-center justify-end gap-2 pt-2">
              <span v-if="justSaved" class="text-xs text-green-600">Saved</span>
              <span v-else-if="isDirty" class="text-xs text-muted-foreground">Unsaved changes</span>
              <Button variant="outline" size="sm" :disabled="isSubmitting" @click="cancelEditing">Cancel</Button>
              <Button
                size="sm"
                :disabled="isSubmitting || (!isDirty && !isSubmitting)"
                @click="onSubmit"
              >
                <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
                Save
              </Button>
            </div>
          </form>
        </Card>

        <!-- Base Price Card -->
        <Card class="p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Base Price</h3>
            <Button
              v-if="canEditBasePrice"
              variant="ghost"
              size="sm"
              class="text-xs h-7"
              @click="showBasePriceModal = true"
            >
              <Pencil class="w-3.5 h-3.5 mr-1" />
              Edit
            </Button>
          </div>
          <div class="flex items-center gap-3 text-sm">
            <span class="font-medium tabular-nums">{{ formatPrice(plan.priceAmount) }}<span class="text-muted-foreground font-normal"> / {{ formatIntervalShort(plan.intervalMonths) }}</span></span>
            <Badge class="bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none">Flat</Badge>
            <span class="text-muted-foreground text-xs">&middot;</span>
            <span class="text-xs text-muted-foreground">{{ plan.billingTiming === 'IN_ARREARS' ? 'In arrears' : 'In advance' }}</span>
          </div>
        </Card>

        <!-- Features Card -->
        <Card class="p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Features</h3>
            <Button
              v-if="canAddFeatures"
              variant="outline"
              size="sm"
              @click="showAddFeatureDialog = true"
            >
              <Plus class="h-4 w-4 mr-1" />
              Add Feature
            </Button>
          </div>
          <div v-if="currentFeatures.length > 0" class="space-y-0 -mx-1">
            <div
              v-for="feature in currentFeatures"
              :key="feature.id"
              class="flex items-center justify-between px-3 py-2.5 rounded-md cursor-pointer hover:bg-muted/50 transition-colors"
              @click="openFeatureDrawer(feature)"
            >
              <div class="flex items-center gap-2 min-w-0">
                <span class="text-sm font-medium truncate">{{ feature.name }}</span>
                <Badge v-if="feature.pricingType === 'usage_based'" class="bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none tabular-nums text-xs shrink-0">
                  Per Unit ({{ formatUnitPrice(feature.unitPrice) }})
                </Badge>
                <Badge
                  v-else-if="feature.pricingType === 'graduated'"
                  class="bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none text-xs shrink-0"
                >
                  Graduated ({{ feature.tiers?.length ?? 0 }} tiers)
                </Badge>
                <Badge v-else class="bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none text-xs shrink-0">
                  Included
                </Badge>
              </div>
              <DropdownMenu>
                <DropdownMenuTrigger as-child @click.stop>
                  <Button variant="ghost" size="sm" class="h-7 w-7 p-0 shrink-0" aria-label="Feature actions">
                    <MoreHorizontal class="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem @click.stop="openFeatureDrawer(feature)">
                    View Details
                  </DropdownMenuItem>
                  <DropdownMenuItem v-if="canEditFeatures && feature.pricingType !== 'included'" @click.stop="openFeatureDrawerInEdit(feature)">
                    Edit Pricing
                  </DropdownMenuItem>
                  <DropdownMenuItem v-if="canRemoveFeatures" class="text-destructive focus:text-destructive" @click.stop="promptRemoveFeature(feature)">
                    Remove
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>
          <div v-else class="flex flex-col items-center justify-center py-8">
            <Package class="w-10 h-10 text-muted-foreground mb-3" />
            <p v-if="planStatus === 'draft'" class="text-sm text-muted-foreground mb-3">
              Add at least one feature to activate this plan.
            </p>
            <p v-else class="text-sm text-muted-foreground">
              No features added yet.
            </p>
            <Button
              v-if="canAddFeatures"
              variant="outline"
              size="sm"
              class="mt-2"
              @click="showAddFeatureDialog = true"
            >
              <Plus class="w-3.5 h-3.5 mr-1" />
              Add Feature
            </Button>
          </div>
        </Card>

        <!-- Credit Allocations Card -->
        <Card class="p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Credit Allocations</h3>
            <Button
              v-if="planStatus !== 'archived'"
              variant="outline"
              size="sm"
              @click="showAddAllocationDialog = true"
            >
              <Plus class="w-3.5 h-3.5 mr-1" />
              Add Allocation
            </Button>
          </div>

          <div v-if="allocationsLoading" class="py-4">
            <Skeleton class="h-10 w-full mb-2" />
            <Skeleton class="h-10 w-full" />
          </div>

          <template v-else-if="creditAllocations.length > 0">
            <div
              v-for="allocation in creditAllocations"
              :key="allocation.id"
              class="flex items-center justify-between px-3 py-2.5 border-t first:mt-3"
            >
              <div class="flex items-center gap-3">
                <span class="text-sm font-medium">{{ allocation.creditModelName || 'Credit Model' }}</span>
                <Badge class="bg-blue-50 text-blue-700 border border-blue-200/50 shadow-none">
                  {{ allocation.creditAmount }} {{ allocation.denomination || 'credits' }}
                </Badge>
                <Badge v-if="allocation.hardLimit === false" variant="outline" class="text-xs">
                  Soft limit
                </Badge>
                <template v-if="allocation.grantExpiresMonths">
                  <span class="text-muted-foreground text-xs">&middot;</span>
                  <span class="text-xs text-muted-foreground">Expires in {{ allocation.grantExpiresMonths }} months</span>
                </template>
              </div>
              <Button
                v-if="planStatus !== 'archived'"
                variant="ghost"
                size="sm"
                class="h-7 w-7 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
                @click="handleRemoveAllocation(allocation)"
              >
                <Trash2 class="h-4 w-4" />
              </Button>
            </div>
          </template>

          <div v-else class="flex flex-col items-center justify-center py-8">
            <Coins class="w-10 h-10 text-muted-foreground mb-3" />
            <p class="text-sm text-muted-foreground">No credit allocations for this plan.</p>
          </div>
        </Card>
      </div>

      <!-- Subscriptions Tab -->
      <div v-else-if="activeTab === 'subscriptions'" class="space-y-6">
        <Card v-if="planSubscriptions.length > 0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Customer</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Period Start</TableHead>
                <TableHead>Period End</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow
                v-for="sub in planSubscriptions"
                :key="sub.id"
                class="cursor-pointer hover:bg-muted/50"
                @click="router.push(demoPath(`/subscriptions/${sub.id}`))"
              >
                <TableCell class="font-medium">
                  {{ sub.customer.firstName }} {{ sub.customer.lastName }}
                </TableCell>
                <TableCell>
                  <Badge :class="sub.isActive ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50' : 'bg-gray-50 text-gray-600 border border-gray-200/50'" class="shadow-none">
                    {{ sub.isActive ? 'Active' : 'Inactive' }}
                  </Badge>
                </TableCell>
                <TableCell class="text-sm text-muted-foreground tabular-nums">
                  {{ formatDateShort(sub.currentPeriodStart) }}
                </TableCell>
                <TableCell class="text-sm text-muted-foreground tabular-nums">
                  {{ formatDateShort(sub.currentPeriodEnd) }}
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </Card>
        <div v-else class="flex flex-col items-center justify-center py-16">
          <div class="w-12 h-12 rounded-lg bg-muted flex items-center justify-center mb-4">
            <Users class="h-6 w-6 text-muted-foreground" />
          </div>
          <h3 class="text-lg font-medium mb-2">No Subscriptions</h3>
          <p class="text-sm text-muted-foreground text-center max-w-sm">
            No customers are subscribed to this plan yet.
          </p>
        </div>
      </div>

    </div>

    <!-- Not Found -->
    <Card v-else class="p-8 text-center">
      <AlertCircle class="h-8 w-8 text-muted-foreground mx-auto mb-4" />
      <h3 class="text-lg font-medium mb-2">Plan Not Found</h3>
      <p class="text-muted-foreground mb-4">The plan you're looking for doesn't exist.</p>
      <Button variant="outline" @click="router.push(demoPath('/plans'))">
        Back to Plans
      </Button>
    </Card>

    <!-- Modals -->
    <EditBasePriceModal
      v-model:visible="showBasePriceModal"
      :plan="plan"
      @update:success="onPlanUpdated"
    />
    <AddFeatureDialog
      v-model:visible="showAddFeatureDialog"
      :plan-id="planId"
      :linked-features="currentFeatures"
      :plan-status="planStatus"
      @update:success="onFeaturesUpdated"
    />
    <FeaturePricingDrawer
      v-model:visible="showFeatureDrawer"
      :feature="selectedFeature"
      :plan-id="planId"
      :plan-status="planStatus"
      :all-features="currentFeatures"
      :can-edit="canEditFeatures"
      :can-remove="canRemoveFeatures"
      :initial-edit="featureDrawerStartInEdit"
      @remove="promptRemoveFeatureById"
      @update:success="onFeaturesUpdated"
    />

    <!-- Add Credit Allocation Dialog -->
    <Dialog :open="showAddAllocationDialog" @update:open="showAddAllocationDialog = $event">
      <DialogContent class="sm:max-w-[450px]">
        <DialogHeader>
          <DialogTitle>Add Credit Allocation</DialogTitle>
        </DialogHeader>
        <div class="flex flex-col gap-4">
          <div class="flex flex-col gap-2">
            <Label>Credit Model *</Label>
            <Select v-model="allocationCreditModelId">
              <SelectTrigger>
                <SelectValue placeholder="Select a credit model..." />
              </SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="cm in availableCreditModels"
                  :key="cm.id"
                  :value="cm.id"
                >
                  {{ cm.name }} ({{ cm.denomination }})
                </SelectItem>
                <div v-if="availableCreditModels.length === 0" class="px-2 py-1.5 text-sm text-muted-foreground">
                  No credit models yet
                </div>
                <Separator class="my-1" />
                <button
                  type="button"
                  class="w-full flex items-center gap-2 px-2 py-1.5 text-sm text-primary hover:bg-accent rounded-sm cursor-pointer"
                  @mousedown.prevent="openCreateCreditModel"
                >
                  <Plus class="w-4 h-4" />
                  Create new credit model
                </button>
              </SelectContent>
            </Select>
          </div>
          <div class="flex flex-col gap-2">
            <Label>Credit Amount *</Label>
            <Input v-model.number="allocationAmount" type="number" min="1" placeholder="e.g., 1000" />
          </div>
          <div class="flex flex-col gap-2">
            <Label>Grant Expiry (months)</Label>
            <Input v-model.number="allocationExpiryMonths" type="number" min="1" placeholder="Optional" />
            <span class="text-xs text-muted-foreground">Leave blank for non-expiring grants</span>
          </div>
          <div class="flex flex-col gap-2">
            <Label>When credits run out</Label>
            <Select v-model="allocationHardLimitStr">
              <SelectTrigger class="h-9">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="true">Block access (hard limit)</SelectItem>
                <SelectItem value="false">Allow overage billing (soft limit)</SelectItem>
              </SelectContent>
            </Select>
            <span class="text-xs text-muted-foreground">
              {{ allocationHardLimit ? 'Events will be rejected when the credit balance reaches zero.' : 'Usage beyond the credit balance will be billed at the feature\'s overage rate.' }}
            </span>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showAddAllocationDialog = false">Cancel</Button>
          <Button
            :disabled="!allocationCreditModelId || !allocationAmount || isAddingAllocation"
            @click="handleAddAllocation"
          >
            <Loader2 v-if="isAddingAllocation" class="mr-2 h-4 w-4 animate-spin" />
            Add Allocation
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Create Credit Model (inline from allocation dialog) -->
    <CreateCreditModelModal
      :visible="showCreateCreditModelModal"
      @update:visible="(v: boolean) => { showCreateCreditModelModal = v; if (!v) showAddAllocationDialog = true }"
    />

    <!-- Remove Feature Confirmation -->
    <AlertDialog :open="showRemoveConfirm">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Remove Feature</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to remove "{{ featureToRemove?.name }}" from this plan?
            <template v-if="planSubscriptions.length > 0">
              <br /><br />
              This plan has {{ planSubscriptions.length }} active subscription{{ planSubscriptions.length === 1 ? '' : 's' }} that will be affected.
            </template>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel @click="cancelRemoveFeature">Cancel</AlertDialogCancel>
          <Button variant="destructive" @click="confirmRemoveFeature">Remove</Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>

    <!-- Activation Confirmation Dialog -->
    <AlertDialog :open="showActivateConfirm">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Activate Plan</AlertDialogTitle>
          <AlertDialogDescription as="div">
            <p class="mb-3">Ready to make this plan available to customers?</p>
            <p class="mb-3">Once active, customers can subscribe to this plan immediately. You can still update the plan name and description, but the key, billing interval, base price, and billing timing will be locked.</p>
            <div class="space-y-2 text-sm">
              <div class="flex items-center gap-2">
                <CheckCircle2 class="h-4 w-4 text-green-600 shrink-0" />
                <span>Name: {{ plan?.name }}</span>
              </div>
              <div class="flex items-center gap-2">
                <CheckCircle2 class="h-4 w-4 text-green-600 shrink-0" />
                <span>Interval: {{ formatInterval(plan?.intervalMonths ?? null) }}</span>
              </div>
              <div class="flex items-center gap-2">
                <CheckCircle2 class="h-4 w-4 text-green-600 shrink-0" />
                <span>Base price: {{ formatPrice(plan?.priceAmount) }}</span>
              </div>
              <div class="flex items-center gap-2">
                <CheckCircle2 class="h-4 w-4 text-green-600 shrink-0" />
                <span>{{ currentFeatures.length }} feature{{ currentFeatures.length === 1 ? '' : 's' }} configured</span>
              </div>
            </div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel @click="showActivateConfirm = false">Cancel</AlertDialogCancel>
          <Button class="bg-green-600 text-white hover:bg-green-700" @click="confirmActivatePlan">
            <Loader2 v-if="isActivating" class="h-4 w-4 animate-spin mr-1.5" />
            Activate Plan
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>

    <!-- Restore Plan Confirmation Dialog -->
    <AlertDialog :open="showRestoreConfirm">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Restore Plan</AlertDialogTitle>
          <AlertDialogDescription>
            This plan will be restored to active status and available for new subscriptions.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel @click="showRestoreConfirm = false">Cancel</AlertDialogCancel>
          <Button class="bg-green-600 text-white hover:bg-green-700" @click="confirmRestorePlan">
            <Loader2 v-if="isRestoring" class="h-4 w-4 animate-spin mr-1.5" />
            Restore Plan
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>

    <!-- Archive Plan Confirmation Dialog -->
    <AlertDialog :open="showArchiveConfirm">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Archive Plan</AlertDialogTitle>
          <AlertDialogDescription>
            <template v-if="planSubscriptions.length > 0">
              This plan has {{ planSubscriptions.length }} active subscription{{ planSubscriptions.length === 1 ? '' : 's' }}. Archiving will prevent new sign-ups.
            </template>
            <template v-else>
              This plan will be archived and no longer available for new subscriptions. WARNING: You will not be able to restore this plan.
            </template>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel @click="showArchiveConfirm = false">Cancel</AlertDialogCancel>
          <Button variant="destructive" @click="confirmArchivePlan">
            <Loader2 v-if="isArchiving" class="h-4 w-4 animate-spin mr-1.5" />
            Archive Plan
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>

    <!-- Remove Allocation Confirmation Dialog -->
    <AlertDialog :open="showRemoveAllocationConfirm">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Remove Credit Allocation</AlertDialogTitle>
          <AlertDialogDescription as="div">
            <template v-if="allocationAffectedFeatures.length > 0">
              <p>
                This allocation is linked to {{ allocationAffectedFeatures.length }}
                feature{{ allocationAffectedFeatures.length === 1 ? '' : 's' }}:
              </p>
              <ul class="mt-2 ml-4 list-disc text-sm">
                <li v-for="f in allocationAffectedFeatures" :key="f.id">{{ f.name }}</li>
              </ul>
              <p class="mt-2">
                Removing this allocation will also remove {{ allocationAffectedFeatures.length === 1 ? 'this feature' : 'these features' }} from the plan.
              </p>
            </template>
            <template v-else>
              Are you sure you want to remove this credit allocation?
            </template>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel @click="showRemoveAllocationConfirm = false" :disabled="isRemovingAllocation">Cancel</AlertDialogCancel>
          <Button variant="destructive" @click="confirmRemoveAllocation" :disabled="isRemovingAllocation">
            <Loader2 v-if="isRemovingAllocation" class="h-4 w-4 animate-spin mr-1.5" />
            Remove
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { toast } from '@/components/ui/toast/use-toast'
import { useTracking } from '@/lib/tracking'
import { useDemoPrefix } from '@/lib/useDemoPrefix'
import {
  ChevronRight,
  Pencil,
  Plus,
  AlertCircle,
  AlertTriangle,
  RefreshCw,
  ArrowLeft,
  Loader2,
  MoreHorizontal,
  Package,
  Lock,
  Archive,
  CheckCircle2,
 /* RotateCcw,*/
  Trash2,
  Coins,
  Users
} from 'lucide-vue-next'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { Card } from '@/components/ui/card'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from '@/components/ui/alert-dialog'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger
} from '@/components/ui/tooltip'
import { usePlanFeaturesQuery } from '../queries'
import { useActivatePlanMutation, useArchivePlanMutation, useRestorePlanMutation } from '../mutations'
import { useSubscriptionsQuery } from '@/features/subscriptions/queries'
import {
  useDeletePlanFeatureRuleMutation
} from '@/features/plan-features/mutations'
import { usePlanFeatureRulesQuery } from '@/features/plan-features/queries'
import { updatePlanSchema } from '../schemas'
import { updatePlan } from '../api'
import { parseApiError } from '@/lib/parseApiError'
import { usePlanEditability } from '../composables/usePlanEditability'
import EditBasePriceModal from '../components/EditBasePriceModal.vue'
import AddFeatureDialog from '../components/AddFeatureDialog.vue'
import FeaturePricingDrawer from '../components/FeaturePricingDrawer.vue'
import MetadataEditor from '@/shared/components/MetadataEditor.vue'
import type { Plan, PlanStatus, UpdatePlan, LinkedFeature } from '../types'
import type { Subscription } from '@/features/subscriptions/types'
import { formatPrice, formatUnitPrice, formatInterval, formatIntervalShort } from '@/lib/formatters'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import CreateCreditModelModal from '@/features/credits/components/CreateCreditModelModal.vue'
import { useCreditModelsQuery } from '@/features/credits/queries'
import { usePlanCreditAllocationsQuery } from '@/features/credits/queries'
import { useCreatePlanCreditAllocationMutation, useDeletePlanCreditAllocationMutation } from '@/features/credits/mutations'
import type { CreditModel, PlanCreditAllocation } from '@/features/credits/types'
import CopyButton from '@/components/CopyButton.vue'

interface FeatureRule {
  type: string
  value: Record<string, unknown>
  creditModelId?: string
  creditModelName?: string
  creditDenomination?: string
}

const { track } = useTracking()
const route = useRoute()
const router = useRouter()
const { demoPath, isDemo } = useDemoPrefix()
const queryClient = useQueryClient()

const planId = computed(() => route.params.id as string)
const activeTab = ref<'overview' | 'subscriptions'>('overview')

const noopMutation = { mutateAsync: async () => {}, isPending: ref(false) }
const { mutateAsync: activatePlanMutate, isPending: isActivating } = isDemo.value
  ? noopMutation
  : useActivatePlanMutation()
const { mutateAsync: archivePlanMutate, isPending: isArchiving } = isDemo.value
  ? noopMutation
  : useArchivePlanMutation()
const { mutateAsync: restorePlanMutate, isPending: isRestoring } = isDemo.value
  ? noopMutation
  : useRestorePlanMutation()

const { data: planFeaturesData, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : usePlanFeaturesQuery(planId)

const plan = computed<Plan | null>(() => {
  if (isDemo.value) {
    // Read plan from seeder cache
    const cached = queryClient.getQueryData<{ data?: Plan }>(['plan', planId.value])
    return cached?.data || null
  }
  return planFeaturesData.value?.data?.plan || null
})

const planStatus = computed<PlanStatus>(() => (plan.value?.status ?? 'draft').toLowerCase() as PlanStatus)

const statusBadgeClass = computed(() => {
  switch (planStatus.value) {
    case 'active':
      return 'bg-emerald-50 text-emerald-700 border border-emerald-200/50 shadow-none'
    case 'draft':
      return 'bg-amber-50 text-amber-700 border border-amber-200/50 shadow-none'
    case 'archived':
      return 'bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none'
    default:
      return 'bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none'
  }
})

function formatDateShort(dateStr: string | null | undefined): string {
  if (!dateStr) return '\u2014'
  return new Date(dateStr).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

// Subscriptions query
const { data: subscriptionsData } = isDemo.value
  ? { data: ref(null) }
  : useSubscriptionsQuery()

const planSubscriptions = computed(() => {
  if (isDemo.value) {
    // Read all subscriptions from seeder cache and filter by this plan
    const cached = queryClient.getQueryData<{ data?: Array<{ id: string; isActive: boolean; customer: { id: string; firstName: string; lastName: string; email: string; referenceId: string; customerReferenceId: string; phoneNumber: string | null; createdAt: string; modifiedAt: string }; plan: { id: string }; currentPeriodStart: string; currentPeriodEnd: string; intervalMonths: string; gracePeriodDays: number; billingAnchorDay: number; cancelMode: string | null; cancelEffectiveAt: string | null; cancelledAt: string | null; metadata: unknown; scheduledChange: unknown }> }>(['subscriptions', {}])
    return (cached?.data || []).filter(s => s.plan.id === planId.value)
  }
  if (!subscriptionsData.value) return []
  let subs: Subscription[] = []
  if (subscriptionsData.value.data) {
    if ('items' in subscriptionsData.value.data && Array.isArray(subscriptionsData.value.data.items)) {
      subs = subscriptionsData.value.data.items
    } else if (Array.isArray(subscriptionsData.value.data)) {
      subs = subscriptionsData.value.data
    }
  }
  return subs.filter((s) => s.plan.id === planId.value)
})

// Credit allocations
const { data: allocationsData, isLoading: allocationsLoading } = isDemo.value
  ? { data: ref(null), isLoading: ref(false) }
  : usePlanCreditAllocationsQuery(planId)
const creditAllocations = computed<PlanCreditAllocation[]>(() => {
  if (!allocationsData.value?.data) return []
  return Array.isArray(allocationsData.value.data) ? allocationsData.value.data : []
})

const { data: creditModelsData } = isDemo.value
  ? { data: ref(null) }
  : useCreditModelsQuery()
const allCreditModels = computed<CreditModel[]>(() => {
  if (!creditModelsData.value?.data) return []
  return Array.isArray(creditModelsData.value.data) ? creditModelsData.value.data : []
})
const availableCreditModels = computed(() => {
  const usedIds = new Set(creditAllocations.value.map((a) => a.creditModelId))
  return allCreditModels.value.filter((cm) => !usedIds.has(cm.id))
})

const showAddAllocationDialog = ref(false)
const showCreateCreditModelModal = ref(false)

function openCreateCreditModel() {
  showAddAllocationDialog.value = false
  nextTick(() => {
    showCreateCreditModelModal.value = true
  })
}
const allocationCreditModelId = ref('')
const allocationAmount = ref<number | undefined>(undefined)
const allocationExpiryMonths = ref<number | undefined>(undefined)
const allocationHardLimitStr = ref('true')
const allocationHardLimit = computed(() => allocationHardLimitStr.value === 'true')

const { mutateAsync: createAllocation, isPending: isAddingAllocation } = isDemo.value
  ? { mutateAsync: async () => {}, isPending: ref(false) }
  : useCreatePlanCreditAllocationMutation()
const { mutateAsync: deleteAllocation } = isDemo.value
  ? { mutateAsync: async () => {} }
  : useDeletePlanCreditAllocationMutation()

// Allocation removal confirmation state
const showRemoveAllocationConfirm = ref(false)
const allocationToRemove = ref<PlanCreditAllocation | null>(null)
const isRemovingAllocation = ref(false)

const allocationAffectedFeatures = computed(() => {
  if (!allocationToRemove.value) return []
  return currentFeatures.value.filter(f => f.creditModelId === allocationToRemove.value?.creditModelId)
})

async function handleAddAllocation() {
  if (!allocationCreditModelId.value || !allocationAmount.value) return
  try {
    await createAllocation({
      creditModelId: allocationCreditModelId.value,
      planId: planId.value,
      creditAmount: allocationAmount.value,
      grantExpiresMonths: allocationExpiryMonths.value,
      hardLimit: allocationHardLimit.value
    })
    toast({ title: 'Success', description: 'Credit allocation added' })
    showAddAllocationDialog.value = false
    allocationCreditModelId.value = ''
    allocationAmount.value = undefined
    allocationExpiryMonths.value = undefined
    allocationHardLimitStr.value = 'true'
  } catch (error) {
    const parsedError = parseApiError(error)
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
}

function handleRemoveAllocation(allocation: PlanCreditAllocation) {
  allocationToRemove.value = allocation
  showRemoveAllocationConfirm.value = true
}

async function confirmRemoveAllocation() {
  if (!allocationToRemove.value) return
  const allocation = allocationToRemove.value

  try {
    isRemovingAllocation.value = true

    // Remove affected features from the plan first
    const affected = currentFeatures.value.filter(f => f.creditModelId === allocation.creditModelId)
    if (affected.length > 0) {
      await Promise.all(affected.map(f =>
        deletePlanFeatureRule({ planUuid: planId.value, featureUuid: f.id })
      ))
    }

    // Then delete the allocation
    await deleteAllocation({
      creditModelId: allocation.creditModelId,
      planId: planId.value
    })

    queryClient.invalidateQueries({ queryKey: ['plan-features', planId.value] })
    toast({ title: 'Success', description: 'Credit allocation removed' })
    showRemoveAllocationConfirm.value = false
    allocationToRemove.value = null
  } catch (error) {
    const parsedError = parseApiError(error)
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  } finally {
    isRemovingAllocation.value = false
  }
}

// Editability composable
const {
  canEditInterval,
  canEditKey,
  canEditBasePrice,
  canEditFeatures,
  canAddFeatures,
  canRemoveFeatures
} = usePlanEditability(planStatus)

// Type for features from API
interface ApiFeature {
  id: string
  name: string
  key: string
  description: string | null
  createdAt: string
  modifiedAt: string
  isEnabled: boolean
}

// Feature IDs derived from plan features data
const featureIds = computed(() =>
  ((planFeaturesData.value?.data?.features || []) as ApiFeature[]).map((f) => f.id)
)

// Fetch rules for all features via Vue Query
const { data: featureRulesData } = usePlanFeatureRulesQuery(planId, featureIds)

// Build a lookup map from the query results (same shape the template expects)
const featureRules = computed(() => {
  const map = new Map<string, FeatureRule>()
  if (!featureRulesData.value) return map
  for (const result of featureRulesData.value) {
    if (result.rule?.data) {
      const ruleData = result.rule.data as Record<string, unknown>
      map.set(result.featureId, {
        type: result.rule.data.type,
        value: result.rule.data.value,
        creditModelId: ruleData.creditModelId as string | undefined,
        creditModelName: ruleData.creditModelName as string | undefined,
        creditDenomination: ruleData.creditDenomination as string | undefined
      })
    }
    // 404 = no rule, feature included by default (not in map)
    // Non-404 errors are logged for debugging
    if (result.error && !result.is404) {
      console.error(`Failed to fetch rule for feature ${result.featureId}:`, result.error)
    }
  }
  return map
})

const currentFeatures = computed<LinkedFeature[]>(() => {
  if (isDemo.value) {
    const cached = queryClient.getQueryData<{ data?: { features?: Array<{ id: string; name: string; key: string; description: string; createdAt: string; modifiedAt: string; isEnabled: boolean }> } }>(['plan-features', planId.value])
    return (cached?.data?.features || []).map(f => ({
      id: f.id, name: f.name, key: f.key, description: f.description,
      createdAt: f.createdAt, modifiedAt: f.modifiedAt, isEnabled: f.isEnabled,
      pricingType: 'included' as const, type: 'BASE', value: {}
    }))
  }
  const features = (planFeaturesData.value?.data?.features || []) as ApiFeature[]
  return features.map((f) => {
    // Get rule data from the fetched rules
    const rule = featureRules.value.get(f.id)
    const ruleValue = rule?.value as Record<string, unknown> | undefined
    const pricingObj = ruleValue?.pricing as Record<string, unknown> | undefined
    const pricingModel = pricingObj?.model ?? ruleValue?.model
    const isGraduated = pricingModel === 'graduated'
    const isUsageBased = pricingModel === 'usage'
    const pricing = rule?.value?.pricing as {
      price_per_unit?: number
      usage_unit_type?: string
      reset_mode?: string
      max_usage?: number
      tiers?: Array<{ up_to: number | 'inf'; price_per_unit: number; flat_fee?: number }>
    } | undefined
    const cost = rule?.value?.cost as {
      cost_per_unit?: number
    } | undefined
    // Fallback to flat value for legacy rules
    const value = rule?.value as Record<string, unknown> | undefined

    let pricingType: 'included' | 'usage_based' | 'graduated' = 'included'
    if (isGraduated) pricingType = 'graduated'
    else if (isUsageBased) pricingType = 'usage_based'

    return {
      id: f.id,
      name: f.name,
      key: f.key,
      description: f.description,
      createdAt: f.createdAt,
      modifiedAt: f.modifiedAt,
      isEnabled: f.isEnabled,
      pricingType,
      unitPrice: pricing?.price_per_unit ?? (value?.price_per_unit as number | undefined),
      unitLabel: pricing?.usage_unit_type ?? (value?.usage_unit_type as string | undefined),
      costPerUnit: cost?.cost_per_unit ?? (value?.cost_per_unit as number | undefined),
      tiers: pricing?.tiers ?? (value?.tiers as Array<{ up_to: number | 'inf'; price_per_unit: number; flat_fee?: number }> | undefined),
      billingTiming: value?.billing_timing as string | undefined,
      type: rule?.type || 'BASE',
      value: rule?.value || {},
      creditModelId: rule?.creditModelId,
      creditModelName: rule?.creditModelName,
      creditDenomination: rule?.creditDenomination
    }
  })
})


// More details toggle
const showMoreDetails = ref(false)

// Inline editing state
const manualEditingActive = ref(false)
const isEditing = computed(() => {
  if (planStatus.value === 'archived') return false
  return manualEditingActive.value
})

const showBasePriceModal = ref(false)
const showAddFeatureDialog = ref(false)
const showFeatureDrawer = ref(false)
const selectedFeature = ref<LinkedFeature | null>(null)
const showRemoveConfirm = ref(false)
const showActivateConfirm = ref(false)
const showRestoreConfirm = ref(false)
const showArchiveConfirm = ref(false)
const featureToRemove = ref<LinkedFeature | null>(null)
const errorMessage = ref<string | null>(null)
const metadata = ref<Record<string, unknown> | null>(null)
const justSaved = ref(false)
let justSavedTimer: ReturnType<typeof setTimeout> | null = null

// Form setup
const { defineField, handleSubmit, errors, setValues, resetForm, setFieldError } = useForm({
  validationSchema: toTypedSchema(updatePlanSchema)
})

const [formName] = defineField('name')
const [formKey] = defineField('key')
const [formDescription] = defineField('description')
const [formIntervalMonths] = defineField('intervalMonths')

// Dirty state tracking
const isDirty = computed(() => {
  if (!plan.value) return false
  if (formName.value !== plan.value.name) return true
  if ((formKey.value || '') !== (plan.value.key || '')) return true
  if ((formDescription.value || '') !== (plan.value.description || '')) return true
  if (String(formIntervalMonths.value || '') !== String(plan.value.intervalMonths || '')) return true
  const currentMeta = metadata.value
  const planMeta = plan.value.metadata && Object.keys(plan.value.metadata).length > 0 ? plan.value.metadata : null
  if (JSON.stringify(currentMeta) !== JSON.stringify(planMeta)) return true
  return false
})

// Unsaved-changes guard
onBeforeRouteLeave(() => {
  if (isDirty.value) {
    const answer = window.confirm('You have unsaved changes. Are you sure you want to leave?')
    if (!answer) return false
  }
})

onBeforeUnmount(() => {
  if (justSavedTimer) clearTimeout(justSavedTimer)
})

// Mutation for updating plan
const { mutateAsync: updatePlanMutate, isPending: isUpdatePending } = isDemo.value
  ? { mutateAsync: async () => {}, isPending: ref(false) }
  : useMutation({
    mutationFn: (data: { uuid: string; planData: UpdatePlan }) =>
      updatePlan(data.uuid, data.planData),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plans'] })
      queryClient.invalidateQueries({ queryKey: ['plan-features', planId.value] })
    }
  })

const isSubmitting = computed(() => isUpdatePending.value)

// Initialize form when plan loads
watch(plan, (p) => {
  if (p) {
    setValues({
      name: p.name,
      key: p.key,
      description: p.description || '',
      intervalMonths: p.intervalMonths || ''
    })
    metadata.value = p.metadata && Object.keys(p.metadata).length > 0
      ? { ...p.metadata }
      : null
  }
}, { immediate: true })

// Activation flow
async function handleActivateClick() {
  // Auto-save pending changes before validating
  if (isDirty.value) {
    await onSubmit()
    // If save failed (errorMessage set) or validation failed, abort activation
    if (errorMessage.value) return
    if (Object.keys(errors.value).length > 0) return
  }

  // Validate required fields against the (now up-to-date) plan.value
  const missing: string[] = []
  if (!plan.value?.name) missing.push('name')
  if (!plan.value?.description) missing.push('description')
  if (!plan.value?.intervalMonths) missing.push('billing interval')
  if (plan.value?.priceAmount === null || plan.value?.priceAmount === undefined) missing.push('base price')
  if (currentFeatures.value.length === 0) missing.push('at least 1 feature')

  if (missing.length > 0) {
    toast({
      title: 'Cannot activate plan',
      description: `Missing: ${missing.join(', ')}`,
      variant: 'destructive'
    })
    return
  }

  showActivateConfirm.value = true
}

async function confirmActivatePlan() {
  try {
    await activatePlanMutate(planId.value)
    track('plan_activated')
    showActivateConfirm.value = false
    toast({ title: 'Plan activated', description: `${plan.value?.name} is now active and available for subscriptions.` })
  } catch (error) {
    console.error('Failed to activate plan:', error)
    toast({ title: 'Error', description: 'Failed to activate plan', variant: 'destructive' })
  }
}

async function confirmRestorePlan() {
  try {
    await restorePlanMutate(planId.value)
    track('plan_restored')
    showRestoreConfirm.value = false
    toast({ title: 'Plan restored', description: `${plan.value?.name} is now active and available for subscriptions.` })
  } catch (error) {
    console.error('Failed to restore plan:', error)
    toast({ title: 'Error', description: 'Failed to restore plan', variant: 'destructive' })
  }
}

async function confirmArchivePlan() {
  try {
    await archivePlanMutate(planId.value)
    track('plan_archived')
    showArchiveConfirm.value = false
    toast({ title: 'Plan archived', description: `${plan.value?.name} has been archived.` })
  } catch (error) {
    console.error('Failed to archive plan:', error)
    toast({ title: 'Error', description: 'Failed to archive plan', variant: 'destructive' })
  }
}

function startEditing() {
  if (plan.value) {
    setValues({
      name: plan.value.name,
      key: plan.value.key,
      description: plan.value.description || '',
      intervalMonths: plan.value.intervalMonths || ''
    })
    metadata.value = plan.value.metadata && Object.keys(plan.value.metadata).length > 0
      ? { ...plan.value.metadata }
      : null
  }
  errorMessage.value = null
  manualEditingActive.value = true
}

function cancelEditing() {
  resetForm()
  if (plan.value) {
    setValues({
      name: plan.value.name,
      key: plan.value.key,
      description: plan.value.description || '',
      intervalMonths: plan.value.intervalMonths || ''
    })
    metadata.value = plan.value.metadata && Object.keys(plan.value.metadata).length > 0
      ? { ...plan.value.metadata }
      : null
  }
  errorMessage.value = null
  manualEditingActive.value = false
}

const onSubmit = handleSubmit(async (values) => {
  if (!plan.value?.id) {
    errorMessage.value = 'No plan selected'
    return
  }

  try {
    errorMessage.value = null

    const planData: Record<string, unknown> = {
      name: values.name,
      description: values.description,
      intervalMonths: values.intervalMonths,
      priceAmount: plan.value.priceAmount || 0,
      billingTiming: plan.value.billingTiming || 'IN_ADVANCE',
      metadata: metadata.value
    }
    // Only include key for draft plans (key is immutable after activation)
    if (planStatus.value === 'draft') {
      planData.key = values.key
    }
    await updatePlanMutate({
      uuid: plan.value.id,
      planData: planData as unknown as UpdatePlan
    })
    toast({ title: 'Success', description: 'Plan updated successfully' })
    manualEditingActive.value = false

    // Show brief "Saved" indicator
    justSaved.value = true
    if (justSavedTimer) clearTimeout(justSavedTimer)
    justSavedTimer = setTimeout(() => { justSaved.value = false }, 2000)
  } catch (error) {
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message

    if (parsedError.type === 'duplicate') {
      setFieldError('key', parsedError.message)
    }
  }
})

// Feature rules query automatically refetches when planId changes (reactive query key)

const featureDrawerStartInEdit = ref(false)

function openFeatureDrawer(feature: LinkedFeature) {
  selectedFeature.value = feature
  featureDrawerStartInEdit.value = false
  showFeatureDrawer.value = true
}

function openFeatureDrawerInEdit(feature: LinkedFeature) {
  selectedFeature.value = feature
  featureDrawerStartInEdit.value = true
  showFeatureDrawer.value = true
}

function onPlanUpdated() {
  queryClient.invalidateQueries({ queryKey: ['plan-features', planId.value] })
}

async function onFeaturesUpdated() {
  // Invalidate both plan features and feature rules so they get refetched
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['plan-features', planId.value] }),
    queryClient.invalidateQueries({ queryKey: ['plan-feature-rules', planId.value] }),
  ])
}

const { mutateAsync: deletePlanFeatureRule } = isDemo.value
  ? { mutateAsync: async () => {} }
  : useDeletePlanFeatureRuleMutation()

function promptRemoveFeature(feature: LinkedFeature) {
  featureToRemove.value = feature
  showRemoveConfirm.value = true
}

function promptRemoveFeatureById(featureId: string) {
  const feature = currentFeatures.value.find((f) => f.id === featureId)
  if (feature) {
    promptRemoveFeature(feature)
  }
}

function cancelRemoveFeature() {
  showRemoveConfirm.value = false
  featureToRemove.value = null
}

async function confirmRemoveFeature() {
  if (!featureToRemove.value) return
  await removeFeature(featureToRemove.value.id)
  showRemoveConfirm.value = false
  featureToRemove.value = null
}

async function removeFeature(featureId: string) {
  try {
    showFeatureDrawer.value = false
    await deletePlanFeatureRule({ planUuid: planId.value, featureUuid: featureId })
    queryClient.invalidateQueries({ queryKey: ['plan-features', planId.value] })
    toast({ title: 'Success', description: 'Feature removed from plan' })
  } catch (error) {
    console.error('Failed to remove feature:', error)
    toast({ title: 'Error', description: 'Failed to remove feature', variant: 'destructive' })
  }
}
</script>
