/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tansoflow.tansocore.service.internal.monetization.implementation;

import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Entitlement;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.monetization.PlanFeatureLinkedDto;
import com.tansoflow.tansocore.repository.EntitlementRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EntitlementServiceImpl implements EntitlementService {
    private final CustomerService customerService;
    private final PlanServiceImpl planService;
    private final EntitlementRepository entitlementRepository;

    @Override
    public Entitlement retrieveEntitlement(String referenceCustomerId, String accountUuid, String featureKey) {
        Customer customer = customerService.retrieveCustomerByExternalClientCustomerIdAndAccount(referenceCustomerId, accountUuid);
        return retrieveEntitlementByFeatureKeyAndCustomer(featureKey, customer);
    }

    @Override
    public void deleteEntitlementsBySubscription(Subscription subscription) {
        List<Entitlement> entitlements = entitlementRepository.findAllBySubscription(subscription);
        entitlementRepository.deleteAll(entitlements);
        log.info("Deleted entitlements for subscription {}", subscription.getId());
    }

    @Override
    public void deleteEntitlementsBySubscriptionAndCustomer(Customer customer, Subscription subscription) {
        List<Entitlement> entitlements = entitlementRepository.findAllByCustomerAndSubscription(customer, subscription);
        entitlementRepository.deleteAll(entitlements);
        log.info("Deleted entitlements for customer {} and subscription {}", customer.getId(), subscription.getId());
    }

    @Override
    public void processEntitlementsForSubscription(Subscription subscription) {
        PlanFeatureLinkedDto planFeatureLinkedDto = retrievePlanFeatureLinkFromSubscription(subscription);
        Customer customer = subscription.getCustomer();

        List<FeatureDto> featureDtos = planFeatureLinkedDto.getFeatures();
        List<Entitlement> entitlements = entitlementRepository.findAllByCustomerAndSubscription(customer, subscription);

        removeDanglingFeaturesFromEntitlements(entitlements, featureDtos);
        regrantEntitlementsFromRevoked(entitlements);

        Set<String> entitlementFeatureKeys = entitlements.stream()
                .map(Entitlement::getFeatureKey)
                .collect(Collectors.toSet());

        grantEntitlements(featureDtos, customer, subscription, entitlementFeatureKeys);

    }

    @Override
    @Transactional
    public void processEntitlementRevokeForSubscription(Subscription subscription) {
        Customer customer = subscription.getCustomer();
        Instant revokeAt = Instant.now();

        List<Entitlement> entitlements = entitlementRepository.findAllByCustomerAndSubscription(customer, subscription);

        revokeEntitlements(entitlements, revokeAt);
        log.info("Revoked entitlements for customer {} and subscription {}", customer.getId(), subscription.getId());

    }

    private PlanFeatureLinkedDto retrievePlanFeatureLinkFromSubscription(Subscription subscription) {
        return planService.retrievePlanFeatureLinkByPlanUuid(subscription.getPlan().getId().toString(), subscription.getAccount().getId().toString());

    }

    private void revokeEntitlements(List<Entitlement> entitlements, Instant revokeAt) {
        for (Entitlement entitlement : entitlements) {
            entitlement.setRevokedAt(revokeAt);
        }

        entitlementRepository.saveAll(entitlements);
    }

    private void regrantEntitlementsFromRevoked(List<Entitlement> entitlements) {
        for (Entitlement entitlement : entitlements) {
            if (entitlement.getRevokedAt() != null) {
                entitlement.setRevokedAt(null);
                entitlementRepository.save(entitlement);
                log.info("Regranted entitlement: {} for customer {} and feature {} that was previously revoked", entitlement.getId(),
                        entitlement.getCustomer().getId(), entitlement.getFeatureKey());
            }
        }
    }

    private void removeDanglingFeaturesFromEntitlements(List<Entitlement> entitlements, List<FeatureDto> features) {
        for (Entitlement entitlement : entitlements) {
            if (features.stream().noneMatch(feature -> feature.getKey().equals(entitlement.getFeatureKey()))) {
                entitlementRepository.delete(entitlement);
                log.info("Deleted dangling entitlement for customer {} and feature {}", entitlement.getCustomer().getId(), entitlement.getFeatureKey());
            }
        }
    }

    private Entitlement retrieveEntitlementByFeatureKeyAndCustomer(String featureKey, Customer customer) {
        return entitlementRepository.getEntitlementByFeatureKeyAndCustomer(featureKey, customer)
                .orElse(null);
    }

    @Override
    public boolean isEntitled(String featureKey, Customer customer) {
        return !entitlementRepository
                .findByCustomerAndFeatureKeyAndRevokedAtIsNull(customer, featureKey)
                .isEmpty();
    }

    private void grantEntitlements(List<FeatureDto> featureDtos, Customer customer, Subscription subscription, Set<String> entitlementFeatureKeys) {
        List<FeatureDto> newGrants = findFeaturesWithoutEntitlements(featureDtos, entitlementFeatureKeys);
        for (FeatureDto featureDto : newGrants) {
            createEntitlement(subscription, featureDto, customer);
        }
    }

    private List<FeatureDto> findFeaturesWithoutEntitlements(List<FeatureDto> featureDtos, Set<String> entitlementFeatureKeys) {
        if (entitlementFeatureKeys.isEmpty()) {
            return featureDtos;
        }

        return featureDtos.stream()
                .filter(feature -> !entitlementFeatureKeys.contains(feature.getKey()))
                .toList();
    }

    private void createEntitlement(Subscription subscription, FeatureDto featureDto, Customer customer) {
        Entitlement entitlement = new Entitlement();
        entitlement.setFeatureKey(featureDto.getKey());
        entitlement.setCustomer(customer);
        entitlement.setSubscription(subscription);
        entitlementRepository.save(entitlement);
        log.info("Saved entitlement for customer {} and feature {}", customer.getId(), featureDto.getKey());
    }

}
