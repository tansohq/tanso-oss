<template>
  <main class="max-w-7xl mx-auto px-6 py-8 md:py-12">
    <div class="mb-8">
      <h2 class="text-3xl font-bold mb-2">Invoice History</h2>
      <p class="text-muted-foreground">
        View all your invoices and billing history.
      </p>
    </div>

    <ExampleInvoiceTable :invoices="invoices" />
  </main>
</template>

<script setup lang="ts">
import ExampleInvoiceTable from '../components/ExampleInvoiceTable.vue'
import { useExampleAppState } from '../composables/useExampleAppState'

const { invoices } = useExampleAppState()
</script>
