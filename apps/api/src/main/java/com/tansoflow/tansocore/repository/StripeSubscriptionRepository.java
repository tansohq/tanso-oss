package com.tansoflow.tansocore.repository;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.StripeSubscription;
import com.tansoflow.tansocore.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StripeSubscriptionRepository extends JpaRepository<StripeSubscription, UUID> {
    boolean existsStripeSubscriptionBySubscriptionAndStripeSubscriptionExternalId(Subscription subscription, String stripeSubscriptionExternalId);

    boolean existsStripeSubscriptionBySubscription(Subscription subscription);

    boolean existsStripeSubscriptionByStripeSubscriptionExternalId(String stripeSubscriptionExternalId);

    StripeSubscription findStripeSubscriptionBySubscription(Subscription subscription);

    StripeSubscription findStripeSubscriptionByStripeSubscriptionExternalId(String stripeSubscriptionExternalId);

    StripeSubscription findByStripeSubscriptionExternalIdAndAccount(String stripeSubscriptionExternalId, Account account);
}
