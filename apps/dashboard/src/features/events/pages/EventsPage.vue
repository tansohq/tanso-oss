<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-foreground">Events</h1>
        <p class="text-muted-foreground mt-1">
          All observed activity with cost and revenue attribution
        </p>
      </div>
      <div class="flex items-center gap-3">
        <div
          v-if="totalElements > 0"
          class="hidden lg:flex items-center gap-2 text-sm text-muted-foreground bg-muted/50 px-3 py-1.5 rounded-full border whitespace-nowrap"
        >
          <Activity class="h-4 w-4" />
          <span class="font-medium text-foreground"
            >{{ totalElements.toLocaleString() }} events</span
          >
        </div>
        <Button v-if="!isDemo" variant="outline" :disabled="isFetching" @click="refetch">
          <RefreshCw class="h-4 w-4 mr-2" :class="{ 'animate-spin': isFetching }" />
          Refresh
        </Button>
        <Button variant="outline" :disabled="isDownloading" @click="downloadCsv">
          <Loader2 v-if="isDownloading" class="h-4 w-4 mr-2 animate-spin" />
          <Download v-else class="h-4 w-4 mr-2" />
          {{ isDownloading ? 'Exporting...' : 'Export CSV' }}
        </Button>
      </div>
    </div>

    <!-- Filter Bar -->
    <div class="flex flex-wrap items-end gap-3 mb-4">
      <div class="flex flex-col gap-1.5">
        <div class="flex gap-1">
          <Button
            v-for="p in periodOptions"
            :key="p"
            :variant="activePeriod === p ? 'default' : 'outline'"
            size="sm"
            class="h-8 px-3"
            @click="selectPeriod(p)"
          >
            {{ periodButtonLabels[p] }}
          </Button>
        </div>
      </div>

      <template v-if="activePeriod === 'custom'">
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">Start date</label>
          <Input v-model="customStart" type="date" class="w-[150px] h-8" @change="onFilterChange" />
        </div>
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">End date</label>
          <Input v-model="customEnd" type="date" class="w-[150px] h-8" @change="onFilterChange" />
        </div>
      </template>

      <Button variant="outline" size="sm" class="h-8" @click="showFilters = !showFilters">
        <ListFilter class="h-3.5 w-3.5 mr-1.5" />
        Filters
        <Badge
          v-if="activeFilterCount > 0"
          class="ml-1.5 h-4 min-w-4 px-1 text-[10px] bg-foreground text-background rounded-full"
          >{{ activeFilterCount }}</Badge
        >
      </Button>
      <Button
        v-if="hasActiveFilters"
        variant="ghost"
        size="sm"
        class="h-8 text-muted-foreground"
        @click="clearFilters"
      >
        <X class="h-3.5 w-3.5 mr-1" />
        Clear
      </Button>

      <Popover>
        <PopoverTrigger as-child>
          <Button variant="outline" size="sm" class="h-8">
            <Settings2 class="h-3.5 w-3.5 mr-1.5" />
            Columns
          </Button>
        </PopoverTrigger>
        <PopoverContent class="w-[260px] p-3" align="end">
          <!-- Active columns - draggable -->
          <div v-if="visibleColumnIds.length > 0" class="mb-3">
            <div class="text-xs font-medium text-muted-foreground mb-1.5">Active columns</div>
            <VueDraggable
              v-model="visibleColumnIds"
              handle=".drag-handle"
              class="space-y-0.5"
              @end="saveColumnPrefs"
            >
              <div
                v-for="colId in visibleColumnIds"
                :key="'active-' + colId"
                class="flex items-center gap-1.5 text-sm rounded px-1.5 py-1 bg-muted/40 group/col"
              >
                <GripVertical
                  class="drag-handle h-3.5 w-3.5 text-muted-foreground/40 cursor-grab shrink-0"
                />
                <span class="truncate flex-1">{{ columnLabel(colId) }}</span>
                <Button
                  variant="ghost"
                  size="sm"
                  class="h-5 w-5 p-0 text-muted-foreground opacity-0 group-hover/col:opacity-100"
                  @click="toggleColumn(colId)"
                >
                  <X class="h-3 w-3" />
                </Button>
              </div>
            </VueDraggable>
          </div>
          <!-- Hidden columns to add -->
          <div v-if="hiddenColumnIds.length > 0">
            <div class="text-xs font-medium text-muted-foreground mb-1.5">Add columns</div>
            <div class="space-y-0.5 max-h-[180px] overflow-y-auto">
              <button
                v-for="colId in hiddenColumnIds"
                :key="colId"
                class="flex items-center gap-2 text-sm cursor-pointer hover:bg-muted/50 rounded px-1 py-1 w-full text-left"
                @click="toggleColumn(colId)"
              >
                <Plus class="h-3.5 w-3.5 text-muted-foreground" />
                <span class="truncate">{{ columnLabel(colId) }}</span>
              </button>
            </div>
          </div>
          <Button
            variant="ghost"
            size="sm"
            class="w-full mt-2 h-7 text-xs text-muted-foreground"
            @click="resetColumns"
          >
            Reset to default
          </Button>
        </PopoverContent>
      </Popover>

      <Select v-model="activeGroupBy" @update:model-value="onGroupByChange">
        <SelectTrigger class="w-[160px] h-8">
          <Layers class="h-3.5 w-3.5 mr-1.5" />
          <SelectValue placeholder="Group by..." />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="__none__">No grouping</SelectItem>
          <SelectItem value="MODEL">Model</SelectItem>
          <SelectItem value="MODEL_PROVIDER">Provider</SelectItem>
          <SelectItem value="CUSTOMER">Customer</SelectItem>
          <SelectItem value="FEATURE">Feature</SelectItem>
          <SelectItem value="EVENT_NAME">Event Name</SelectItem>
        </SelectContent>
      </Select>
    </div>

    <!-- Filters -->
    <div v-if="showFilters" class="flex flex-wrap items-end gap-3 mb-4">
      <div v-if="!isObserveMode" class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Event Type</label>
        <Select v-model="filterEventType" @update:model-value="onFilterChange">
          <SelectTrigger class="w-[200px] h-8">
            <SelectValue placeholder="All events" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">All Events</SelectItem>
            <SelectItem value="CLIENT_TRACKED">Usage Events</SelectItem>
            <SelectItem value="ENTITLEMENT_CHECKED">Entitlement Checks</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div v-if="isObserveMode" class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Model</label>
        <Input
          v-model="filterModel"
          placeholder="Filter by model..."
          class="w-[180px] h-8"
          @input="onFilterChange"
        />
      </div>

      <div v-if="isObserveMode" class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Provider</label>
        <Select v-model="filterModelProvider" @update:model-value="onFilterChange">
          <SelectTrigger class="w-[160px] h-8">
            <SelectValue placeholder="All providers" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">All providers</SelectItem>
            <SelectItem value="OpenAI">OpenAI</SelectItem>
            <SelectItem value="Anthropic">Anthropic</SelectItem>
            <SelectItem value="Google">Google</SelectItem>
            <SelectItem value="Cohere">Cohere</SelectItem>
            <SelectItem value="Mistral">Mistral</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Customer</label>
        <Select v-model="filterCustomer" @update:model-value="onFilterChange">
          <SelectTrigger class="w-[200px] h-8">
            <SelectValue placeholder="All customers" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">All customers</SelectItem>
            <SelectItem v-for="c in customers" :key="c.referenceId" :value="c.referenceId">
              {{ c.firstName }} {{ c.lastName }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Plan</label>
        <Select v-model="filterPlan" @update:model-value="onFilterChange">
          <SelectTrigger class="w-[180px] h-8">
            <SelectValue placeholder="All plans" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">All plans</SelectItem>
            <SelectItem v-for="p in plans" :key="p.id" :value="p.id">
              {{ p.name }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Feature</label>
        <Select v-model="filterFeature" @update:model-value="onFilterChange">
          <SelectTrigger class="w-[180px] h-8">
            <SelectValue placeholder="All features" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">All features</SelectItem>
            <SelectItem v-for="f in features" :key="f.id" :value="f.id">
              {{ f.name }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Event Name</label>
        <Input
          v-model="filterEventName"
          placeholder="Filter by name..."
          class="w-[200px] h-8"
          @input="onFilterChange"
        />
      </div>
    </div>

    <!-- Grouped View -->
    <div v-if="isGroupedView">
      <div v-if="groupedLoading" class="flex items-center justify-center py-12">
        <RefreshCw class="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
      <GroupedEventsTable
        v-else-if="groupedEvents.length > 0"
        :groups="groupedEvents"
        :group-by="activeGroupByValue!"
        @drill-down="onGroupDrillDown"
      />
      <div
        v-else
        class="flex flex-col items-center justify-center py-12 text-muted-foreground bg-card rounded-lg border shadow-sm"
      >
        <Inbox class="w-12 h-12 mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">No grouped data</p>
        <p class="text-sm">No events match the current filters</p>
      </div>
    </div>

    <!-- Chart -->
    <EventsIngestedCard
      v-if="!isGroupedView && !isError"
      :total-events="chartTotalEvents"
      :is-loading="chartLoading"
      :daily-counts="chartBucketCounts"
      :labels="chartLabels"
      :period-label="periodLabel"
    />

    <!-- Entitlement Check Summary Cards -->
    <div
      v-if="!isGroupedView && isEntitlementFilter && !isError"
      class="grid grid-cols-3 gap-4 mb-4"
    >
      <Card class="p-6">
        <div class="text-sm font-medium text-muted-foreground">Total Checks</div>
        <div class="text-3xl font-semibold tabular-nums mt-1">
          {{ totalElements.toLocaleString() }}
        </div>
        <div class="flex items-center gap-1.5 mt-3 text-sm text-muted-foreground">
          <CheckCircle2 class="h-3.5 w-3.5 text-emerald-500" />
          {{ (totalElements - entitlementDeniedCount).toLocaleString() }} allowed
        </div>
      </Card>
      <Card class="p-6">
        <div class="text-sm font-medium text-muted-foreground">Denial Rate</div>
        <div class="text-3xl font-semibold tabular-nums mt-1">{{ entitlementDenialRate }}%</div>
        <div class="flex items-center gap-1.5 mt-3 text-sm text-muted-foreground">
          <XCircle class="h-3.5 w-3.5 text-red-500" />
          {{ entitlementDeniedCount }} denied
        </div>
      </Card>
    </div>

    <div v-if="!isGroupedView" class="bg-card rounded-lg border shadow-sm">
      <div class="p-4 border-b">
        <div class="relative max-w-sm">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            v-model="searchQuery"
            placeholder="Search by event name, customer, or type..."
            class="pl-9"
          />
        </div>
      </div>
      <TableSkeleton
        v-if="isLoading"
        :columns="['Event Name', 'Customer', 'Type', 'Created At']"
        :rows="5"
        :column-widths="['w-40', 'w-24', 'w-20', 'w-36']"
      />

      <div v-else-if="isError" class="flex flex-col items-center justify-center py-12">
        <AlertCircle class="w-12 h-12 text-destructive mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">Unable to load events</p>
        <p class="text-sm text-muted-foreground mb-4">
          This is usually fixed by logging out and back in.
        </p>
        <div class="flex gap-2">
          <Button variant="outline" @click="refetch">
            <RefreshCw class="w-4 h-4 mr-2" />
            Try Again
          </Button>
          <Button variant="outline" @click="authStore.logout()">
            <LogOut class="w-4 h-4 mr-2" />
            Log out
          </Button>
        </div>
      </div>

      <div v-else-if="events && events.length > 0" class="relative">
        <div
          v-if="isFetching && !isLoading"
          class="absolute inset-0 bg-background/50 z-10 flex items-start justify-center pt-12"
        >
          <RefreshCw class="h-5 w-5 animate-spin text-muted-foreground" />
        </div>
        <div class="overflow-x-auto">
          <Table class="[&_td]:whitespace-nowrap [&_th]:whitespace-nowrap min-w-[900px]">
            <TableHeader>
              <TableRow>
                <template v-for="colId in visibleColumnIds" :key="'h-' + colId">
                  <TableHead
                    v-if="colId === 'time'"
                    class="cursor-pointer hover:bg-muted/50"
                    @click="toggleSort('occurredAt')"
                  >
                    Time
                    <component
                      :is="getSortIcon('occurredAt')"
                      class="ml-2 h-4 w-4 inline"
                      :class="{ 'text-primary': sortField === 'occurredAt' }"
                    />
                  </TableHead>
                  <TableHead
                    v-else-if="colId === 'eventName'"
                    class="cursor-pointer hover:bg-muted/50"
                    @click="toggleSort('eventName')"
                  >
                    Event Name
                    <component
                      :is="getSortIcon('eventName')"
                      class="ml-2 h-4 w-4 inline"
                      :class="{ 'text-primary': sortField === 'eventName' }"
                    />
                  </TableHead>
                  <TableHead
                    v-else-if="colId === 'customer'"
                    class="cursor-pointer hover:bg-muted/50"
                    @click="toggleSort('customerId')"
                  >
                    Customer
                    <component
                      :is="getSortIcon('customerId')"
                      class="ml-2 h-4 w-4 inline"
                      :class="{ 'text-primary': sortField === 'customerId' }"
                    />
                  </TableHead>
                  <TableHead
                    v-else-if="colId === 'type'"
                    class="cursor-pointer hover:bg-muted/50"
                    @click="toggleSort('eventType')"
                  >
                    Type
                    <component
                      :is="getSortIcon('eventType')"
                      class="ml-2 h-4 w-4 inline"
                      :class="{ 'text-primary': sortField === 'eventType' }"
                    />
                  </TableHead>
                  <TableHead
                    v-else-if="colId === 'cost'"
                    class="text-right cursor-pointer hover:bg-muted/50"
                    @click="toggleSort('costAmount')"
                  >
                    Cost
                    <component
                      :is="getSortIcon('costAmount')"
                      class="ml-2 h-4 w-4 inline"
                      :class="{ 'text-primary': sortField === 'costAmount' }"
                    />
                  </TableHead>
                  <TableHead
                    v-else-if="colId === 'revenue'"
                    class="text-right cursor-pointer hover:bg-muted/50"
                    @click="toggleSort('revenueAmount')"
                  >
                    Revenue
                    <component
                      :is="getSortIcon('revenueAmount')"
                      class="ml-2 h-4 w-4 inline"
                      :class="{ 'text-primary': sortField === 'revenueAmount' }"
                    />
                  </TableHead>
                  <TableHead v-else-if="colId === 'margin'" class="text-right">Margin</TableHead>
                  <TableHead v-else class="max-w-[200px]">{{ columnLabel(colId) }}</TableHead>
                </template>
                <TableHead class="w-10" />
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow
                v-for="event in sortedEvents"
                :key="event.id"
                class="cursor-pointer hover:bg-muted/50 transition-colors group"
                @click="onRowClick(event)"
              >
                <template v-for="colId in visibleColumnIds" :key="'c-' + colId + '-' + event.id">
                  <!-- Time -->
                  <TableCell
                    v-if="colId === 'time'"
                    class="tabular-nums text-sm text-muted-foreground"
                  >
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger as-child>
                          <span>{{ formatDateTime(event.occurredAt) }}</span>
                        </TooltipTrigger>
                        <TooltipContent>
                          <p class="text-xs tabular-nums">
                            {{ formatRelativeTime(event.occurredAt) }}
                          </p>
                        </TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  </TableCell>
                  <!-- Event Name -->
                  <TableCell v-else-if="colId === 'eventName'">
                    <div class="flex items-center gap-1.5">
                      <span class="text-sm font-medium">{{
                        formatEventName(event.eventName)
                      }}</span>
                      <AlertTriangle
                        v-if="event.ingestError"
                        class="h-4 w-4 text-amber-500 shrink-0"
                        aria-label="Ingest error"
                      />
                    </div>
                  </TableCell>
                  <!-- Customer -->
                  <TableCell v-else-if="colId === 'customer'" class="text-sm">
                    <span
                      v-if="event.customerId"
                      class="cursor-pointer hover:text-foreground hover:underline transition-colors"
                      @click.stop="copyToClipboard(event.customerId)"
                      :title="event.customerId"
                      >{{ customerNameById.get(event.customerId) ?? event.customerId }}</span
                    >
                    <span v-else class="text-muted-foreground/40">&mdash;</span>
                  </TableCell>
                  <!-- Type -->
                  <TableCell v-else-if="colId === 'type'">
                    <Badge
                      v-if="event.eventType === 'CLIENT_TRACKED'"
                      class="shadow-none bg-gray-50 text-gray-600 border border-gray-200/50 font-normal"
                      >Usage</Badge
                    >
                    <Badge
                      v-else-if="
                        event.eventType === 'ENTITLEMENT_CHECKED' &&
                        event.properties?.isEntitled === true
                      "
                      class="shadow-none bg-emerald-50 text-emerald-700 border border-emerald-200/50 font-normal"
                    >
                      <CheckCircle2 class="h-3 w-3 mr-1" />Entitled
                    </Badge>
                    <Badge
                      v-else-if="
                        event.eventType === 'ENTITLEMENT_CHECKED' &&
                        event.properties?.isEntitled === false
                      "
                      class="shadow-none bg-red-50 text-red-700 border border-red-200/50 font-normal"
                    >
                      <XCircle class="h-3 w-3 mr-1" />Denied
                    </Badge>
                    <Badge
                      v-else-if="event.eventType === 'ENTITLEMENT_CHECKED'"
                      class="shadow-none bg-violet-50 text-violet-700 border border-violet-200/50 font-normal"
                      >Entitlement</Badge
                    >
                    <Badge
                      v-else-if="event.eventType"
                      class="shadow-none bg-gray-50 text-gray-600 border border-gray-200/50 font-normal"
                      >{{ event.eventType }}</Badge
                    >
                    <span v-else class="text-muted-foreground/40">&mdash;</span>
                  </TableCell>
                  <!-- Cost -->
                  <TableCell v-else-if="colId === 'cost'" class="text-right tabular-nums text-sm">
                    <span v-if="event.costAmount != null" class="font-medium text-foreground">{{
                      formatCost(Number(event.costAmount))
                    }}</span>
                    <span v-else class="text-muted-foreground/40">&mdash;</span>
                  </TableCell>
                  <!-- Revenue -->
                  <TableCell
                    v-else-if="colId === 'revenue'"
                    class="text-right tabular-nums text-sm"
                  >
                    <span v-if="event.revenueAmount != null" class="font-medium text-foreground">{{
                      formatCost(Number(event.revenueAmount))
                    }}</span>
                    <span v-else class="text-muted-foreground/40">&mdash;</span>
                  </TableCell>
                  <!-- Margin -->
                  <TableCell v-else-if="colId === 'margin'" class="text-right tabular-nums text-sm">
                    <span
                      v-if="
                        event.costAmount != null &&
                        event.revenueAmount != null &&
                        Number(event.revenueAmount) > 0
                      "
                      :class="
                        Number(event.revenueAmount) - Number(event.costAmount) >= 0
                          ? 'text-green-600 dark:text-green-400'
                          : 'text-red-600 dark:text-red-400'
                      "
                      >{{
                        (
                          ((Number(event.revenueAmount) - Number(event.costAmount)) /
                            Number(event.revenueAmount)) *
                          100
                        ).toFixed(0)
                      }}%</span
                    >
                    <span v-else class="text-muted-foreground/40">&mdash;</span>
                  </TableCell>
                  <!-- Meta column -->
                  <TableCell
                    v-else
                    class="text-sm max-w-[200px] truncate"
                    :title="String(getMetaValue(event, colId) ?? '')"
                  >
                    <span v-if="getMetaValue(event, colId) != null">{{
                      formatMetaValue(getMetaValue(event, colId))
                    }}</span>
                    <span v-else class="text-muted-foreground/40">&mdash;</span>
                  </TableCell>
                </template>
                <TableCell>
                  <DropdownMenu>
                    <DropdownMenuTrigger as-child>
                      <Button
                        variant="ghost"
                        size="sm"
                        class="h-7 w-7 p-0 opacity-0 group-hover:opacity-100 transition-opacity"
                        @click.stop
                      >
                        <MoreHorizontal class="h-4 w-4 text-muted-foreground" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem @click.stop="onRowClick(event)">
                        <Eye class="h-4 w-4 mr-2" />View details
                      </DropdownMenuItem>
                      <DropdownMenuItem @click.stop="copyEventJson(event)">
                        <Braces class="h-4 w-4 mr-2" />Copy as JSON
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>

        <div v-if="totalElements > 0" class="flex items-center justify-between px-4 py-4 border-t">
          <div class="flex items-center gap-4">
            <div class="text-sm text-muted-foreground">
              <template v-if="searchQuery.trim()">
                Showing {{ sortedEvents.length }} of {{ totalElements.toLocaleString() }} events
              </template>
              <template v-else>
                Showing
                <span class="font-medium text-foreground">{{
                  (currentPage * pageSize + 1).toLocaleString()
                }}</span>
                to
                <span class="font-medium text-foreground">{{
                  Math.min((currentPage + 1) * pageSize, totalElements).toLocaleString()
                }}</span>
                of
                <span class="font-medium text-foreground">{{
                  totalElements.toLocaleString()
                }}</span>
                events
              </template>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-sm text-muted-foreground">Rows per page:</span>
              <Select
                :model-value="String(pageSize)"
                @update:model-value="
                  (v) => {
                    pageSize = Number(v)
                    currentPage = 0
                  }
                "
              >
                <SelectTrigger class="w-[70px] h-8">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="25">25</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div v-if="!searchQuery.trim()" class="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              :disabled="currentPage === 0"
              @click="currentPage--"
            >
              Previous
            </Button>
            <span class="text-sm text-muted-foreground">
              Page {{ currentPage + 1 }} of {{ totalPages }}
            </span>
            <Button
              variant="outline"
              size="sm"
              :disabled="currentPage >= totalPages - 1"
              @click="currentPage++"
            >
              Next
            </Button>
          </div>
        </div>
      </div>

      <div v-else class="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <Inbox class="w-12 h-12 mb-4" />
        <template v-if="hasActiveFilters">
          <p class="text-lg font-medium text-foreground mb-2">No events match your filters</p>
          <p class="text-sm mb-4">Try adjusting your search criteria or clearing filters</p>
          <Button variant="outline" @click="clearFilters">
            <X class="h-4 w-4 mr-2" />
            Clear filters
          </Button>
        </template>
        <template v-else>
          <p class="text-lg font-medium text-foreground mb-2">No events yet</p>
          <p class="text-sm">Events will appear here as they are tracked</p>
        </template>
      </div>
    </div>

    <EventDetailsModal
      v-model:visible="showModal"
      :event="selectedEvent"
      :can-go-prev="canNavigatePrev"
      :can-go-next="canNavigateNext"
      @navigate="navigateEvent"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Button } from '@/components/ui/button'

import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import {
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Search,
  Inbox,
  AlertCircle,
  AlertTriangle,
  RefreshCw,
  LogOut,
  Download,
  Loader2,
  ListFilter,
  X,
  CheckCircle2,
  XCircle,
  Activity,
  Braces,
  MoreHorizontal,
  Eye,
  Layers,
  Settings2,
  Plus,
  GripVertical
} from 'lucide-vue-next'
import { VueDraggable } from 'vue-draggable-plus'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Card } from '@/components/ui/card'
import { toast } from '@/components/ui/toast/use-toast'
import TableSkeleton from '@/shared/components/TableSkeleton.vue'
import EventDetailsModal from '../components/EventDetailsModal.vue'
import EventsIngestedCard from '../components/EventsIngestedCard.vue'
import GroupedEventsTable from '../components/GroupedEventsTable.vue'
import { useEventsQuery, useGroupedEventsQuery } from '../queries'
import { fetchEvents } from '../api'
import { queryClient } from '@/lib/queryClient'
import { useCustomersQuery } from '@/features/customers/queries'
import { usePlansQuery } from '@/features/plans/queries'
import { useFeaturesQuery } from '@/features/features/queries'
import { useAccountSettingsQuery } from '@/features/integrations/queries'
import { useAuthStore } from '@/stores/auth'
import { getDateRange, getPresetLabel, type EventsPeriodPreset } from '@/shared/utils/datePresets'
import { formatDateTime, formatCost } from '@/lib/formatters'
import type { Event, GroupBy, EventGroup } from '../types'
import { useTracking } from '@/lib/tracking'
import { useDemoPrefix } from '@/lib/useDemoPrefix'

const route = useRoute()
const { isDemo } = useDemoPrefix()
const authStore = isDemo.value ? { logout: () => {} } : useAuthStore()
const { track } = useTracking()
const { data: settingsData } = useAccountSettingsQuery()
const isObserveMode = computed(() => settingsData.value?.data?.platformMode === 'OBSERVE')
const searchQuery = ref('')
const currentPage = ref(0)
const pageSize = ref(25)
const showModal = ref(false)
const selectedEvent = ref<Event | null>(null)
const sortField = ref<string | null>('occurredAt')
const sortOrder = ref<'asc' | 'desc'>('desc')
const showFilters = ref(false)
const activeGroupBy = ref<string>('__none__')
const activeGroupByValue = computed<GroupBy | undefined>(() =>
  activeGroupBy.value && activeGroupBy.value !== '__none__'
    ? (activeGroupBy.value as GroupBy)
    : undefined
)
const isGroupedView = computed(() => !!activeGroupByValue.value)

function onGroupByChange() {
  track('event_group_by_changed', { groupBy: activeGroupBy.value })
}

// Convert an ISO string or date string to YYYY-MM-DD for date inputs
function toDateInputValue(val: string | undefined | null): string {
  if (!val) return ''
  const d = new Date(val)
  if (isNaN(d.getTime())) return ''
  return d.toISOString().slice(0, 10)
}

// Period presets
const periodOptions: EventsPeriodPreset[] = ['7d', '30d', '3m', '12m', 'custom']
const periodButtonLabels: Record<EventsPeriodPreset, string> = {
  '7d': '7D',
  '30d': '30D',
  '3m': '3M',
  '12m': '12M',
  custom: 'Custom'
}

// Initialize period from route query params
const hasRouteDate = !!(route.query.start || route.query.end)
const activePeriod = ref<EventsPeriodPreset>(hasRouteDate ? 'custom' : '7d')
const customStart = ref(toDateInputValue(route.query.start as string))
const customEnd = ref(toDateInputValue(route.query.end as string))

function selectPeriod(preset: EventsPeriodPreset) {
  track('event_period_changed', { period: preset })
  activePeriod.value = preset
  if (preset !== 'custom') {
    customStart.value = ''
    customEnd.value = ''
  }
  currentPage.value = 0
}

// Entity filter state
const filterCustomer = ref<string | undefined>(
  (route.query.customerReferenceId as string) || undefined
)
const filterPlan = ref<string | undefined>((route.query.planId as string) || undefined)
const filterFeature = ref<string | undefined>((route.query.featureId as string) || undefined)
const filterEventType = ref<string | undefined>((route.query.eventType as string) || undefined)
const filterEventName = ref('')
const debouncedEventName = ref('')
const filterModel = ref<string | undefined>((route.query.model as string) || undefined)
const filterModelProvider = ref<string | undefined>(undefined)

// Debounce event name filter (300ms)
let eventNameTimeout: ReturnType<typeof setTimeout> | null = null
watch(filterEventName, (val) => {
  if (eventNameTimeout) clearTimeout(eventNameTimeout)
  eventNameTimeout = setTimeout(() => {
    debouncedEventName.value = val
    currentPage.value = 0
  }, 300)
})

// Computed filter values
const activeCustomerFilter = computed(() =>
  filterCustomer.value && filterCustomer.value !== '__all__' ? filterCustomer.value : undefined
)
const activePlanFilter = computed(() =>
  filterPlan.value && filterPlan.value !== '__all__' ? filterPlan.value : undefined
)
const activeFeatureFilter = computed(() =>
  filterFeature.value && filterFeature.value !== '__all__' ? filterFeature.value : undefined
)
const activeEventTypeFilter = computed(() =>
  filterEventType.value && filterEventType.value !== '__all__' ? filterEventType.value : undefined
)
const activeModelFilter = computed(() =>
  filterModel.value && filterModel.value.trim() ? filterModel.value.trim() : undefined
)
const activeModelProviderFilter = computed(() =>
  filterModelProvider.value && filterModelProvider.value !== '__all__'
    ? filterModelProvider.value
    : undefined
)
const activeEventNameFilter = computed(() =>
  debouncedEventName.value.trim() ? debouncedEventName.value.trim() : undefined
)

// Date range derived from period preset or custom inputs
const activeDateRange = computed(() => {
  if (activePeriod.value === 'custom') {
    const start = customStart.value ? new Date(customStart.value) : undefined
    let end: Date | undefined
    if (customEnd.value) {
      end = new Date(customEnd.value)
      end.setHours(23, 59, 59, 999)
    }
    return { start, end }
  }
  return getDateRange(activePeriod.value)
})

const activeStartFilter = computed(() => activeDateRange.value.start?.toISOString())
const activeEndFilter = computed(() => activeDateRange.value.end?.toISOString())

const periodLabel = computed(() => {
  if (activePeriod.value === 'custom' && customStart.value && customEnd.value) {
    return `${customStart.value} – ${customEnd.value}`
  }
  return getPresetLabel(activePeriod.value)
})

const activeFilterCount = computed(() => {
  let count = 0
  if (activeCustomerFilter.value) count++
  if (activePlanFilter.value) count++
  if (activeFeatureFilter.value) count++
  if (activeEventTypeFilter.value) count++
  if (activeModelFilter.value) count++
  if (activeModelProviderFilter.value) count++
  if (filterEventName.value.trim()) count++
  return count
})

const hasActiveFilters = computed(
  () =>
    activeCustomerFilter.value ||
    activePlanFilter.value ||
    activeFeatureFilter.value ||
    activeEventTypeFilter.value ||
    activeModelFilter.value ||
    activeModelProviderFilter.value ||
    filterEventName.value.trim() ||
    activePeriod.value !== '7d'
)

// Auto-expand filters panel if entity filters are active (e.g. from route params or analytics drill-down)
if (activeFilterCount.value > 0) {
  showFilters.value = true
}

function onFilterChange() {
  currentPage.value = 0
}

function clearFilters() {
  track('event_filters_cleared')
  filterCustomer.value = undefined
  filterPlan.value = undefined
  filterFeature.value = undefined
  filterEventType.value = undefined
  filterModel.value = undefined
  filterModelProvider.value = undefined
  filterEventName.value = ''
  debouncedEventName.value = ''
  activePeriod.value = '7d'
  customStart.value = ''
  customEnd.value = ''
  activeGroupBy.value = '__none__'
  currentPage.value = 0
}

// Grouped events query
const { data: groupedData, isLoading: groupedLoading } = isDemo.value
  ? { data: ref(null), isLoading: ref(false) }
  : useGroupedEventsQuery({
      groupBy: activeGroupByValue,
      customerReferenceId: activeCustomerFilter,
      planId: activePlanFilter,
      featureId: activeFeatureFilter,
      start: activeStartFilter,
      end: activeEndFilter,
      eventType: activeEventTypeFilter,
      model: activeModelFilter,
      modelProvider: activeModelProviderFilter,
      eventName: activeEventNameFilter
    })

const groupedEvents = computed<EventGroup[]>(() => groupedData.value?.data ?? [])

function onGroupDrillDown(group: EventGroup) {
  track('event_group_drill_down', { groupBy: activeGroupBy.value, groupKey: group.groupKey })
  const groupByVal = activeGroupByValue.value
  activeGroupBy.value = '__none__'
  showFilters.value = true
  if (groupByVal === 'MODEL') {
    filterModel.value = group.groupKey
  } else if (groupByVal === 'MODEL_PROVIDER') {
    filterModelProvider.value = group.groupKey
  } else if (groupByVal === 'CUSTOMER') {
    filterCustomer.value = group.groupKey
  } else if (groupByVal === 'FEATURE') {
    filterFeature.value = group.groupKey
  } else if (groupByVal === 'EVENT_NAME') {
    filterEventName.value = group.groupKey
  }
  currentPage.value = 0
}

// Dropdown data
const { data: customersData } = isDemo.value ? { data: ref(null) } : useCustomersQuery()
const { data: plansData } = isDemo.value ? { data: ref(null) } : usePlansQuery()
const { data: featuresData } = isDemo.value ? { data: ref(null) } : useFeaturesQuery()

const customers = computed(() => customersData.value?.data?.customers ?? [])
const plans = computed(() => plansData.value?.data ?? [])
const features = computed(() => featuresData.value?.data ?? [])

const DEMO_CUSTOMER_NAMES: Record<string, string> = {
  'a1b2c3d4-e5f6-4789-abcd-ef0123456789': 'Sarah Chen',
  'b2c3d4e5-f6a7-4890-bcde-f01234567890': 'Marcus Rodriguez',
  'c3d4e5f6-a7b8-4901-cdef-012345678901': 'Emily Park',
  'd4e5f6a7-b8c9-4012-def0-123456789012': 'James Mitchell',
  'e5f6a7b8-c9d0-4123-ef01-234567890123': 'Anika Patel'
}

const customerNameById = computed(() => {
  const map = new Map<string, string>()
  if (isDemo.value) {
    for (const [id, name] of Object.entries(DEMO_CUSTOMER_NAMES)) {
      map.set(id, name)
    }
  } else {
    for (const c of customers.value) {
      const fullName = [c.firstName, c.lastName].filter(Boolean).join(' ').trim()
      map.set(c.id, fullName || c.email || c.referenceId || c.id)
    }
  }
  return map
})

// Table query (paginated)
const { data, isLoading, isError, isFetching, refetch } = isDemo.value
  ? {
      data: ref(null),
      isLoading: ref(false),
      isError: ref(false),
      isFetching: ref(false),
      refetch: () => {}
    }
  : useEventsQuery({
      page: currentPage,
      size: pageSize,
      customerReferenceId: activeCustomerFilter,
      planId: activePlanFilter,
      featureId: activeFeatureFilter,
      start: activeStartFilter,
      end: activeEndFilter,
      eventType: activeEventTypeFilter,
      model: activeModelFilter,
      modelProvider: activeModelProviderFilter,
      eventName: activeEventNameFilter
    })

// Generate realistic demo events spread across 7 days with growing volume
const DEMO_EVENTS: Event[] = (() => {
  // IDs aligned with useDemoDataSeeder
  const eventDefs = [
    {
      name: 'chat_completion',
      feature: 'f6a7b8c9-d0e1-4234-f012-345678901234',
      featureKey: 'api_access',
      unitType: 'tokens',
      unitsRange: [80, 4200] as const,
      unitPrice: 0.00006,
      costRate: 0.00002,
      model: 'gpt-4o',
      modelProvider: 'OpenAI'
    },
    {
      name: 'contact_enriched',
      feature: 'd0e1f2a3-b4c5-4678-3456-789012345678',
      featureKey: 'contact_enrichment',
      unitType: 'enrichments',
      unitsRange: [1, 1] as const,
      unitPrice: 0.15,
      costRate: 0.04,
      model: 'claude-3-5-sonnet',
      modelProvider: 'Anthropic'
    },
    {
      name: 'email_generated',
      feature: 'c9d0e1f2-a3b4-4567-2345-678901234567',
      featureKey: 'email_sequences',
      unitType: 'emails',
      unitsRange: [1, 25] as const,
      unitPrice: 0.008,
      costRate: 0.002,
      model: 'gpt-4o',
      modelProvider: 'OpenAI'
    },
    {
      name: 'campaign_analyzed',
      feature: 'a7b8c9d0-e1f2-4345-0123-456789012345',
      featureKey: 'campaign_analytics',
      unitType: 'calls',
      unitsRange: [1, 50] as const,
      unitPrice: 0.001,
      costRate: 0.0002,
      model: 'gpt-4o-mini',
      modelProvider: 'OpenAI'
    },
    {
      name: 'crm_synced',
      feature: 'b8c9d0e1-f2a3-4456-1234-567890123456',
      featureKey: 'crm_integrations',
      unitType: 'syncs',
      unitsRange: [1, 10] as const,
      unitPrice: 0.005,
      costRate: 0.001,
      model: 'gpt-4o-mini',
      modelProvider: 'OpenAI'
    },
    {
      name: 'embedding_generated',
      feature: 'f6a7b8c9-d0e1-4234-f012-345678901234',
      featureKey: 'api_access',
      unitType: 'tokens',
      unitsRange: [1000, 10000] as const,
      unitPrice: 0.00001,
      costRate: 0.000005,
      model: 'embed-english-v3.0',
      modelProvider: 'Cohere'
    }
  ]
  const customerIds = [
    'a1b2c3d4-e5f6-4789-abcd-ef0123456789', // outbound
    'b2c3d4e5-f6a7-4890-bcde-f01234567890', // pipelineai
    'c3d4e5f6-a7b8-4901-cdef-012345678901', // prospectlab
    'd4e5f6a7-b8c9-4012-def0-123456789012', // dealflow
    'e5f6a7b8-c9d0-4123-ef01-234567890123' // salesforge
  ]
  const customerRefs = [
    'cus_outbound',
    'cus_pipelineai',
    'cus_prospectlab',
    'cus_dealflow',
    'cus_salesforge'
  ]
  const subIds = [
    '11111111-aaaa-4bbb-cccc-dddddddddddd',
    '22222222-bbbb-4ccc-dddd-eeeeeeeeeeee',
    '33333333-cccc-4ddd-eeee-ffffffffffff',
    '44444444-dddd-4eee-ffff-aaaaaaaaaaaa',
    '55555555-eeee-4fff-aaaa-bbbbbbbbbbbb'
  ]
  const events: Event[] = []
  const now = Date.now()

  // Seeded random for deterministic demo data
  let seed = 42
  const rand = () => {
    seed = (seed * 16807 + 0) % 2147483647
    return seed / 2147483647
  }

  // ~200 events over 7 days with growth pattern
  const eventsPerDay = [18, 22, 26, 28, 32, 38, 48]
  let idx = 0
  for (let day = 6; day >= 0; day--) {
    const count = eventsPerDay[6 - day]
    for (let j = 0; j < count; j++) {
      const hourOffset = Math.floor(rand() * 24)
      const minOffset = Math.floor(rand() * 60)
      const ts = new Date(
        now - day * 86400000 - hourOffset * 3600000 - minOffset * 60000
      ).toISOString()
      const isEntitlementCheck = j % 6 === 0
      const custIdx = Math.floor(rand() * customerIds.length)
      const defIdx = Math.floor(rand() * eventDefs.length)
      const def = eventDefs[defIdx]
      const units = isEntitlementCheck
        ? 0
        : Math.floor(rand() * (def.unitsRange[1] - def.unitsRange[0] + 1)) + def.unitsRange[0]
      const isDenied = isEntitlementCheck && rand() < 0.12
      const hasError = false

      events.push({
        id: `evt_${String(idx).padStart(4, '0')}`,
        eventType: isEntitlementCheck ? 'ENTITLEMENT_CHECKED' : 'CLIENT_TRACKED',
        eventName: def.name,
        customerId: customerIds[custIdx],
        customerReferenceId: customerRefs[custIdx],
        occurredAt: ts,
        createdAt: ts,
        modifiedAt: ts,
        accountId: '00000000-0000-0000-0000-000000000000',
        usageUnits: units,
        usageUnitType: def.featureKey,
        featureId: def.feature,
        featureKey: def.featureKey,
        model: def.model,
        modelProvider: def.modelProvider,
        subscriptionId: subIds[custIdx],
        entitlementId: null,
        invoiceId: null,
        revenueAmount: isEntitlementCheck ? null : parseFloat((units * def.unitPrice).toFixed(6)),
        revenueUnit: isEntitlementCheck ? null : 'USD',
        costAmount: isEntitlementCheck ? null : parseFloat((units * def.costRate).toFixed(6)),
        costUnit: isEntitlementCheck ? null : 'USD',
        properties: isEntitlementCheck ? { isEntitled: !isDenied } : {},
        eventIdempotencyKey: `demo_${idx}_${day}_${j}`,
        flowId: isEntitlementCheck ? `flow_${idx}` : null,
        meta: !isEntitlementCheck
          ? {
              inputTokens: Math.floor(units * 0.6),
              outputTokens: Math.floor(units * 0.4),
              source: ['sdk', 'api', 'webhook'][Math.floor(rand() * 3)]
            }
          : null,
        context: !isEntitlementCheck
          ? {
              sys_cost_source: 'account_default',
              sys_model: def.model,
              sys_model_provider: def.modelProvider,
              sys_applied_rule_id: `rule_${def.feature}`,
              sys_captured_unit_price: def.unitPrice,
              sys_pricing_model: defIdx === 0 ? 'graduated' : 'usage'
            }
          : null,
        customerIsNative: true,
        featureIsNative: true,
        subscriptionIsNative: true,
        entitlementIsNative: null,
        invoiceIsNative: null,
        ingestError: hasError ? 'Feature key not found on active subscription' : null
      } satisfies Event)
      idx++
    }
  }
  return events.sort(
    (a, b) => new Date(b.occurredAt!).getTime() - new Date(a.occurredAt!).getTime()
  )
})()

const events = computed(() => {
  const raw = isDemo.value ? DEMO_EVENTS : (data.value?.data?.items ?? [])
  // In Observe mode, hide entitlement checks — they have no cost/model data
  if (isObserveMode.value) return raw.filter((e: Event) => e.eventType !== 'ENTITLEMENT_CHECKED')
  return raw
})

// Column configuration (must be after `events` computed)
const BUILT_IN_COLUMNS: Record<string, string> = {
  time: 'Time',
  eventName: 'Event Name',
  customer: 'Customer',
  type: 'Type',
  cost: 'Cost',
  revenue: 'Revenue',
  margin: 'Margin'
}
const BUILT_IN_IDS = Object.keys(BUILT_IN_COLUMNS)
const DEFAULT_COLUMNS = isObserveMode.value
  ? BUILT_IN_IDS.filter((id) => id !== 'type')
  : [...BUILT_IN_IDS]

const COLUMN_PREFS_KEY = 'tanso_events_columns_v2'
const visibleColumnIds = ref<string[]>(loadColumnPrefs())

function loadColumnPrefs(): string[] {
  try {
    const saved = localStorage.getItem(COLUMN_PREFS_KEY)
    if (saved) return JSON.parse(saved)
  } catch {
    // ignore
  }
  return [...DEFAULT_COLUMNS]
}

function saveColumnPrefs() {
  localStorage.setItem(COLUMN_PREFS_KEY, JSON.stringify(visibleColumnIds.value))
}

function resetColumns() {
  visibleColumnIds.value = [...DEFAULT_COLUMNS]
  saveColumnPrefs()
}

function toggleColumn(colId: string) {
  const idx = visibleColumnIds.value.indexOf(colId)
  if (idx >= 0) {
    visibleColumnIds.value.splice(idx, 1)
  } else {
    visibleColumnIds.value.push(colId)
  }
  saveColumnPrefs()
}

function columnLabel(colId: string): string {
  if (BUILT_IN_COLUMNS[colId]) return BUILT_IN_COLUMNS[colId]
  // Format camelCase/snake_case to Title Case
  return colId
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

const availableMetaKeys = computed(() => {
  const keys = new Set<string>()
  for (const event of events.value) {
    if (event.meta) {
      for (const k of Object.keys(event.meta)) keys.add(k)
    }
    if (event.properties) {
      for (const k of Object.keys(event.properties)) keys.add(k)
    }
  }
  return Array.from(keys).sort()
})

const allColumnIds = computed(() => {
  const ids = [...BUILT_IN_IDS]
  for (const k of availableMetaKeys.value) {
    if (!ids.includes(k)) ids.push(k)
  }
  return ids
})

const hiddenColumnIds = computed(() =>
  allColumnIds.value.filter((id) => !visibleColumnIds.value.includes(id))
)

const hasColumnPrefs = localStorage.getItem(COLUMN_PREFS_KEY) !== null
if (!hasColumnPrefs) {
  watch(
    availableMetaKeys,
    (keys) => {
      for (const k of keys) {
        if (!visibleColumnIds.value.includes(k)) {
          visibleColumnIds.value.push(k)
        }
      }
    },
    { immediate: true }
  )
}

function getMetaValue(event: Event, key: string): unknown {
  return event.meta?.[key] ?? event.properties?.[key] ?? null
}

function formatMetaValue(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

const totalPages = computed(() =>
  isDemo.value
    ? Math.ceil(filteredEvents.value.length / pageSize.value)
    : (data.value?.data?.totalPages ?? 0)
)
const totalElements = computed(() =>
  isDemo.value ? filteredEvents.value.length : (data.value?.data?.totalElements ?? 0)
)

// Chart query (same filters, large page to get enough data for bucketing)
const chartSize = computed(() => 500)
const chartPage = computed(() => 0)
const { data: chartData, isLoading: chartLoading } = isDemo.value
  ? { data: ref(null), isLoading: ref(false) }
  : useEventsQuery({
      page: chartPage,
      size: chartSize,
      customerReferenceId: activeCustomerFilter,
      planId: activePlanFilter,
      featureId: activeFeatureFilter,
      start: activeStartFilter,
      end: activeEndFilter,
      eventType: activeEventTypeFilter
    })

const chartEvents = computed(() =>
  isDemo.value ? DEMO_EVENTS : (chartData.value?.data?.items ?? [])
)
const chartTotalEvents = computed(() =>
  isDemo.value ? DEMO_EVENTS.length : (chartData.value?.data?.totalElements ?? 0)
)

// Entitlement check summary cards
const isEntitlementFilter = computed(() => activeEventTypeFilter.value === 'ENTITLEMENT_CHECKED')
const entitlementDeniedCount = computed(() => {
  if (!isEntitlementFilter.value) return 0
  return chartEvents.value.filter((e: Event) => e.properties?.isEntitled === false).length
})
const entitlementDenialRate = computed(() => {
  if (!isEntitlementFilter.value || chartEvents.value.length === 0) return 0
  return Math.round((entitlementDeniedCount.value / chartEvents.value.length) * 100)
})

// Bucket chart events into counts based on active period
const chartBucketCounts = computed(() => {
  const items = chartEvents.value
  const range = activeDateRange.value
  if (!range.start || !range.end) return []

  const useDayBuckets =
    activePeriod.value === '7d' || activePeriod.value === '30d' || activePeriod.value === 'custom'

  if (useDayBuckets) {
    const startMs = range.start.getTime()
    const endMs = range.end.getTime()
    const dayMs = 1000 * 60 * 60 * 24
    const numDays = Math.max(1, Math.ceil((endMs - startMs) / dayMs))
    const counts = Array(numDays).fill(0)

    for (const event of items) {
      if (!event.occurredAt) continue
      const eventMs = new Date(event.occurredAt).getTime()
      const dayIndex = Math.floor((eventMs - startMs) / dayMs)
      if (dayIndex >= 0 && dayIndex < numDays) {
        counts[dayIndex]++
      }
    }
    return counts
  }

  // Month buckets for 3m / 12m
  const startYear = range.start.getFullYear()
  const startMonth = range.start.getMonth()
  const endYear = range.end.getFullYear()
  const endMonth = range.end.getMonth()
  const numMonths = (endYear - startYear) * 12 + (endMonth - startMonth) + 1
  const counts = Array(numMonths).fill(0)

  for (const event of items) {
    if (!event.occurredAt) continue
    const d = new Date(event.occurredAt)
    const monthIndex = (d.getFullYear() - startYear) * 12 + (d.getMonth() - startMonth)
    if (monthIndex >= 0 && monthIndex < numMonths) {
      counts[monthIndex]++
    }
  }
  return counts
})

// Generate labels matching the bucket counts
const chartLabels = computed(() => {
  const range = activeDateRange.value
  if (!range.start || !range.end) return []

  const useDayBuckets =
    activePeriod.value === '7d' || activePeriod.value === '30d' || activePeriod.value === 'custom'

  if (useDayBuckets) {
    const dayCount = chartBucketCounts.value.length
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
    const labels: string[] = []
    const d = new Date(range.start)

    if (dayCount <= 7) {
      // Show day-of-week for short ranges
      for (let i = 0; i < dayCount; i++) {
        labels.push(days[d.getDay()])
        d.setDate(d.getDate() + 1)
      }
    } else {
      // Show sparse "MMM D" labels for longer ranges
      const step = dayCount <= 14 ? 2 : dayCount <= 31 ? 5 : 7
      for (let i = 0; i < dayCount; i++) {
        if (i % step === 0 || i === dayCount - 1) {
          labels.push(d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }))
        } else {
          labels.push('')
        }
        d.setDate(d.getDate() + 1)
      }
    }
    return labels
  }

  // Month labels for 3m / 12m
  const months = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec'
  ]
  const labels: string[] = []
  const d = new Date(range.start.getFullYear(), range.start.getMonth(), 1)
  const numMonths = chartBucketCounts.value.length
  for (let i = 0; i < numMonths; i++) {
    labels.push(months[d.getMonth()])
    d.setMonth(d.getMonth() + 1)
  }
  return labels
})

// Table filtering and sorting
const filteredEvents = computed(() => {
  let result = events.value

  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (event: Event) =>
        event.eventType?.toLowerCase().includes(query) ||
        event.eventName?.toLowerCase().includes(query) ||
        event.customerId?.toLowerCase().includes(query)
    )
  }

  return result
})

const sortedEvents = computed(() => {
  let result = filteredEvents.value
  if (sortField.value) {
    result = [...result].sort((a, b) => {
      const aVal = a[sortField.value as keyof Event]
      const bVal = b[sortField.value as keyof Event]
      if (aVal === null || aVal === undefined) return 1
      if (bVal === null || bVal === undefined) return -1
      if (aVal < bVal) return sortOrder.value === 'asc' ? -1 : 1
      if (aVal > bVal) return sortOrder.value === 'asc' ? 1 : -1
      return 0
    })
  }
  if (isDemo.value) {
    const start = currentPage.value * pageSize.value
    return result.slice(start, start + pageSize.value)
  }
  return result
})

function toggleSort(field: string) {
  if (sortField.value === field) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortOrder.value = 'asc'
  }
}

function getSortIcon(field: string) {
  if (sortField.value !== field) return ArrowUpDown
  return sortOrder.value === 'asc' ? ArrowUp : ArrowDown
}

watch(searchQuery, () => {
  currentPage.value = 0
})

function formatEventName(name: string | null | undefined): string {
  if (!name) return '—'
  return name.replace(/[_-]/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
}

function formatRelativeTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '\u2014'
  const now = new Date()
  const date = new Date(dateStr)
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return 'Just now'
  if (diffMins < 60) return `${diffMins}m ago`
  if (diffHours < 24) return `${diffHours}h ago`
  if (diffDays < 7) return `${diffDays}d ago`
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

function onRowClick(event: Event) {
  track('event_detail_opened', { eventId: event.id })
  selectedEvent.value = event
  showModal.value = true
}

// Keyboard navigation for event drawer
const selectedEventIndex = computed(() => {
  if (!selectedEvent.value) return -1
  return sortedEvents.value.findIndex((e: Event) => e.id === selectedEvent.value!.id)
})
const canNavigatePrev = computed(() => selectedEventIndex.value > 0)
const canNavigateNext = computed(
  () => selectedEventIndex.value >= 0 && selectedEventIndex.value < sortedEvents.value.length - 1
)

function navigateEvent(direction: -1 | 1) {
  const idx = selectedEventIndex.value + direction
  if (idx >= 0 && idx < sortedEvents.value.length) {
    selectedEvent.value = sortedEvents.value[idx]
  }
}

// Arrow key navigation when drawer is open
function handleKeydown(e: KeyboardEvent) {
  if (!showModal.value) return
  if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
    e.preventDefault()
    navigateEvent(e.key === 'ArrowUp' ? -1 : 1)
  }
}

watch(showModal, (open) => {
  if (open) {
    window.addEventListener('keydown', handleKeydown)
  } else {
    window.removeEventListener('keydown', handleKeydown)
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (eventNameTimeout) clearTimeout(eventNameTimeout)
})

const isDownloading = ref(false)

function escapeCsvField(value: string): string {
  if (value.includes(',') || value.includes('"') || value.includes('\n')) {
    return `"${value.replace(/"/g, '""')}"`
  }
  return value
}

async function downloadCsv() {
  track('events_exported_to_csv')
  isDownloading.value = true
  try {
    // Fetch all events matching current filters
    const allEvents: Event[] = []
    const batchSize = 100
    let page = 0
    let totalPages = 1

    while (page < totalPages) {
      const response = await queryClient.fetchQuery({
        queryKey: [
          'events',
          'csv-export',
          {
            page,
            size: batchSize,
            customerReferenceId: activeCustomerFilter.value,
            planId: activePlanFilter.value,
            featureId: activeFeatureFilter.value,
            start: activeStartFilter.value,
            end: activeEndFilter.value,
            eventType: activeEventTypeFilter.value,
            model: activeModelFilter.value,
            modelProvider: activeModelProviderFilter.value,
            eventName: activeEventNameFilter.value
          }
        ],
        queryFn: () =>
          fetchEvents({
            page,
            size: batchSize,
            customerReferenceId: activeCustomerFilter.value,
            planId: activePlanFilter.value,
            featureId: activeFeatureFilter.value,
            start: activeStartFilter.value,
            end: activeEndFilter.value,
            eventType: activeEventTypeFilter.value,
            model: activeModelFilter.value,
            modelProvider: activeModelProviderFilter.value,
            eventName: activeEventNameFilter.value
          })
      })
      const data = response?.data
      if (!data?.items) break
      allEvents.push(...data.items)
      totalPages = data.totalPages
      page++
    }

    if (allEvents.length === 0) {
      toast({
        title: 'No data',
        description: 'No events match the current filters',
        variant: 'destructive'
      })
      return
    }

    const headers = [
      'occurred_at',
      'event_type',
      'event_name',
      'customer',
      'feature_id',
      'feature_key',
      'model',
      'model_provider',
      'entitled',
      'usage_units',
      'usage_unit_type',
      'input_tokens',
      'output_tokens',
      'revenue_amount',
      'cost_amount',
      'margin',
      'meta',
      'properties',
      'event_id',
      'created_at'
    ]
    const rows = allEvents.map((e) => {
      const margin =
        e.costAmount != null && e.revenueAmount != null && Number(e.revenueAmount) > 0
          ? (
              ((Number(e.revenueAmount) - Number(e.costAmount)) / Number(e.revenueAmount)) *
              100
            ).toFixed(1) + '%'
          : ''
      return [
        e.occurredAt ?? '',
        e.eventType ?? '',
        e.eventName ?? '',
        customerNameById.value.get(e.customerId ?? '') ?? e.customerId ?? '',
        e.featureId ?? '',
        e.featureKey ?? '',
        e.model ?? '',
        e.modelProvider ?? '',
        e.properties?.isEntitled !== undefined ? String(e.properties.isEntitled) : '',
        String(e.usageUnits ?? ''),
        e.usageUnitType ?? '',
        e.inputTokens != null ? String(e.inputTokens) : '',
        e.outputTokens != null ? String(e.outputTokens) : '',
        e.revenueAmount != null ? String(e.revenueAmount) : '',
        e.costAmount != null ? String(e.costAmount) : '',
        margin,
        e.meta ? JSON.stringify(e.meta) : '',
        e.properties ? JSON.stringify(e.properties) : '',
        e.id,
        e.createdAt ?? ''
      ].map(escapeCsvField)
    })

    const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `events-${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 100)

    toast({ title: 'Downloaded', description: `Exported ${allEvents.length} events to CSV` })
  } catch {
    toast({ title: 'Error', description: 'Failed to export events', variant: 'destructive' })
  } finally {
    isDownloading.value = false
  }
}

async function copyEventJson(event: Event) {
  try {
    await navigator.clipboard.writeText(JSON.stringify(event, null, 2))
    toast({ title: 'Copied', description: 'Event JSON copied to clipboard' })
  } catch {
    toast({ title: 'Error', description: 'Failed to copy', variant: 'destructive' })
  }
}

async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast({ title: 'Copied', description: 'ID copied to clipboard' })
  } catch {
    toast({ title: 'Error', description: 'Failed to copy', variant: 'destructive' })
  }
}
</script>
