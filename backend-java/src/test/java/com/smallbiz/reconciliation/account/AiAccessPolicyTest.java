package com.smallbiz.reconciliation.account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.smallbiz.reconciliation.aireview.ai.AiReviewException;

class AiAccessPolicyTest {
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();}
    @Test void defaultKillSwitchDeniesProAndEnabledSwitchStillDeniesGlobalTools() {
        var account=UUID.randomUUID();var tenant=UUID.randomUUID();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new AccountPrincipal(account,"owner","hash"),null,List.of()));
        SecurityContextHolder.setContext(context);
        var auth=mock(AuthService.class);var tenants=mock(TenantAccess.class);
        var pro=new AuthService.TenantResponse(tenant,"Shop","PRO");
        when(auth.me()).thenReturn(new AuthService.MeResponse(account,"owner",List.of(pro)));
        when(tenants.require(tenant)).thenReturn(pro);
        assertThatThrownBy(()->new AiAccessPolicy(auth,tenants,false).requireReviewAccess())
                .isInstanceOfSatisfying(AiReviewException.class,ex->assertThat(ex.code()).isEqualTo("AI_DISABLED"));
        assertThatThrownBy(()->new AiAccessPolicy(auth,tenants,true).requireReviewAccess())
                .isInstanceOfSatisfying(AiReviewException.class,ex->assertThat(ex.code()).isEqualTo("AI_TENANT_TOOLS_UNAVAILABLE"));
    }
    @Test void missingAuthenticationCannotReachMembershipLookups() {
        var auth=mock(AuthService.class);var tenants=mock(TenantAccess.class);
        assertThatThrownBy(()->new AiAccessPolicy(auth,tenants,true).requireReviewAccess()).isInstanceOf(AccountException.class);
        verifyNoInteractions(auth,tenants);
    }
}
