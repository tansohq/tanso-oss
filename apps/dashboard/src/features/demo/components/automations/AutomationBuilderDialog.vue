<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent class="max-w-2xl">
      <DialogHeader>
        <DialogTitle>{{ automationId ? 'Edit Automation' : 'Create Automation' }}</DialogTitle>
        <DialogDescription>
          Set up a trigger and actions that run automatically.
        </DialogDescription>
      </DialogHeader>

      <div class="space-y-6 mt-4">
        <!-- Automation Name -->
        <div>
          <Label for="name">Automation Name</Label>
          <Input
            id="name"
            v-model="automationName"
            placeholder="e.g., Margin Rescue - High Value"
            class="mt-1"
            autofocus
          />
        </div>

        <!-- Trigger -->
        <div>
          <Label>When</Label>
          <Select v-model="triggerType" class="mt-1">
            <SelectTrigger>
              <SelectValue placeholder="Select a trigger..." />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="segment_entry">Customer enters segment</SelectItem>
              <SelectItem value="segment_exit">Customer exits segment</SelectItem>
              <SelectItem value="margin_threshold">Margin threshold crossed</SelectItem>
              <SelectItem value="cost_spike">Cost spike detected</SelectItem>
              <SelectItem value="usage_threshold">Usage threshold exceeded</SelectItem>
              <SelectItem value="time_in_segment">Time in segment exceeded</SelectItem>
              <SelectItem value="ramp_rate_below">Ramp rate below average</SelectItem>
              <SelectItem value="scheduled">Scheduled (daily/weekly)</SelectItem>
            </SelectContent>
          </Select>

          <!-- Trigger Config -->
          <div
            v-if="triggerType === 'segment_entry' || triggerType === 'segment_exit'"
            class="mt-3"
          >
            <Label class="text-sm text-muted-foreground">Segment</Label>
            <Select v-model="triggerSegment">
              <SelectTrigger class="mt-1">
                <SelectValue placeholder="Select segment..." />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="segment_001">Loss-Making Accounts</SelectItem>
                <SelectItem value="segment_002">Margin Erosion</SelectItem>
                <SelectItem value="segment_003">High-Margin Expansion</SelectItem>
                <SelectItem value="segment_004">Power Users</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div v-if="triggerType === 'time_in_segment'" class="mt-3 flex items-center gap-2">
            <Label class="text-sm text-muted-foreground">For more than</Label>
            <Input v-model="triggerThreshold" type="number" class="w-20" />
            <Select v-model="triggerUnit" class="w-28">
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="hours">hours</SelectItem>
                <SelectItem value="days">days</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <!-- Conditions (Optional) -->
        <div>
          <Label>Only trigger if (optional)</Label>
          <div class="mt-2 space-y-2">
            <div
              v-for="(condition, index) in conditions"
              :key="index"
              class="flex items-center gap-2"
            >
              <Select v-model="condition.field" class="w-32">
                <SelectTrigger>
                  <SelectValue placeholder="Field" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="mrr">MRR</SelectItem>
                  <SelectItem value="margin">Margin</SelectItem>
                  <SelectItem value="plan">Plan</SelectItem>
                </SelectContent>
              </Select>
              <Select v-model="condition.operator" class="w-28">
                <SelectTrigger>
                  <SelectValue placeholder="Op" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="at_least">≥</SelectItem>
                  <SelectItem value="at_most">≤</SelectItem>
                </SelectContent>
              </Select>
              <Input v-model="condition.value" class="w-24" placeholder="Value" />
              <Button variant="ghost" size="icon" @click="conditions.splice(index, 1)">
                <X class="h-4 w-4" />
              </Button>
            </div>
          </div>
          <Button variant="outline" size="sm" class="mt-2" @click="addCondition">
            <Plus class="h-4 w-4 mr-1" />
            Add condition
          </Button>
        </div>

        <!-- Actions -->
        <div>
          <Label>Then do the following</Label>
          <div class="mt-2 space-y-3">
            <div v-for="(action, index) in actions" :key="index" class="p-3 border rounded-lg">
              <div class="flex items-center justify-between mb-2">
                <span class="text-sm font-medium">Action {{ index + 1 }}</span>
                <Button variant="ghost" size="icon" @click="actions.splice(index, 1)">
                  <X class="h-4 w-4" />
                </Button>
              </div>
              <Select v-model="action.type">
                <SelectTrigger>
                  <SelectValue placeholder="Select action type..." />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="slack">Send Slack message</SelectItem>
                  <SelectItem value="email">Send email</SelectItem>
                  <SelectItem value="in_app">Show in-app message</SelectItem>
                  <SelectItem value="crm_update">Update CRM record</SelectItem>
                  <SelectItem value="create_task">Create task</SelectItem>
                </SelectContent>
              </Select>

              <div v-if="action.type === 'slack'" class="mt-2">
                <Label class="text-xs text-muted-foreground">Channel</Label>
                <Select v-model="action.channel">
                  <SelectTrigger class="mt-1">
                    <SelectValue placeholder="Select channel..." />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="#margin-alerts">#margin-alerts</SelectItem>
                    <SelectItem value="#sales">#sales</SelectItem>
                    <SelectItem value="#cs-alerts">#cs-alerts</SelectItem>
                    <SelectItem value="#finops">#finops</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          <Button variant="outline" size="sm" class="mt-2" @click="addAction">
            <Plus class="h-4 w-4 mr-1" />
            Add action
          </Button>
        </div>

        <!-- Frequency Cap -->
        <div>
          <Label>Frequency cap</Label>
          <div class="flex items-center gap-2 mt-2 text-sm">
            <span>Trigger max</span>
            <Input v-model="frequencyMax" type="number" class="w-16" />
            <span>time(s) per customer every</span>
            <Input v-model="frequencyPeriod" type="number" class="w-16" />
            <span>days</span>
          </div>
        </div>
      </div>

      <DialogFooter class="mt-6">
        <Button variant="outline" @click="$emit('update:open', false)">Cancel</Button>
        <Button @click="saveAutomation">Save Automation</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { Plus, X } from 'lucide-vue-next'

defineProps<{
  open: boolean
  automationId: string | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const automationName = ref('')
const triggerType = ref('')
const triggerSegment = ref('')
const triggerThreshold = ref(48)
const triggerUnit = ref('hours')

interface Condition {
  field: string
  operator: string
  value: string
}

const conditions = ref<Condition[]>([])

interface Action {
  type: string
  channel?: string
}

const actions = ref<Action[]>([{ type: '' }])

const frequencyMax = ref(1)
const frequencyPeriod = ref(30)

function addCondition() {
  conditions.value.push({ field: '', operator: '', value: '' })
}

function addAction() {
  actions.value.push({ type: '' })
}

function saveAutomation() {
  emit('update:open', false)
}
</script>
