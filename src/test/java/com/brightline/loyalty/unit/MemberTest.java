package com.brightline.loyalty.unit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.brightline.loyalty.domain.Member;
import com.brightline.loyalty.domain.MembershipTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberTest {

    @Test
    @DisplayName("A valid member exposes the id, name and tier it was created with")
    void shouldExposeValuesOfValidMember() {
        Member member = new Member("m1", "Kofi Mensah", MembershipTier.SILVER);

        assertAll(
                () -> assertEquals("m1", member.id()),
                () -> assertEquals("Kofi Mensah", member.name()),
                () -> assertEquals(MembershipTier.SILVER, member.tier()));
    }

    @ParameterizedTest(name = "member id [{0}] is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectNullOrBlankId(String id) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new Member(id, "Kofi Mensah", MembershipTier.GOLD));

        assertEquals("Member id must not be null or blank", failure.getMessage());
    }

    @ParameterizedTest(name = "member name [{0}] is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectNullOrBlankName(String name) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new Member("m1", name, MembershipTier.GOLD));

        assertEquals("Member name must not be null or blank", failure.getMessage());
    }

    @Test
    @DisplayName("A member without a tier cannot exist, so no member can have an unsupported tier")
    void shouldRejectNullTier() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new Member("m1", "Kofi Mensah", null));

        assertEquals("Membership tier must not be null", failure.getMessage());
    }
}
