package com.brightline.loyalty.unit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.brightline.loyalty.domain.Member;
import com.brightline.loyalty.domain.MembershipTier;
import com.brightline.loyalty.repository.InMemoryMemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryMemberRepositoryTest {

    private static final Member KOFI = new Member("m1", "Kofi Mensah", MembershipTier.STANDARD);
    private static final Member AMA = new Member("m2", "Ama Owusu", MembershipTier.GOLD);

    @Test
    @DisplayName("Finds each member that was supplied, by id")
    void shouldFindSuppliedMembersById() {
        InMemoryMemberRepository repository = new InMemoryMemberRepository(List.of(KOFI, AMA));

        assertAll(
                () -> assertEquals(Optional.of(KOFI), repository.findById("m1")),
                () -> assertEquals(Optional.of(AMA), repository.findById("m2")));
    }

    @Test
    @DisplayName("An id that was not supplied is reported as an empty Optional, not null")
    void shouldReturnEmptyForUnknownId() {
        InMemoryMemberRepository repository = new InMemoryMemberRepository(List.of(KOFI));

        assertEquals(Optional.empty(), repository.findById("nobody"));
    }

    @Test
    @DisplayName("The sample repository holds the three members the original application shipped")
    void shouldProvideTheOriginalSampleMembers() {
        InMemoryMemberRepository repository = InMemoryMemberRepository.withSampleMembers();

        assertAll(
                () -> assertEquals(Optional.of(KOFI), repository.findById("m1")),
                () -> assertEquals(Optional.of(AMA), repository.findById("m2")),
                () -> assertEquals(
                        Optional.of(new Member("m3", "Yaa Boateng", MembershipTier.SILVER)),
                        repository.findById("m3")));
    }

    @Test
    @DisplayName("Two members with the same id are rejected rather than one overwriting the other")
    void shouldRejectDuplicateMemberIds() {
        Member impostor = new Member("m1", "Someone Else", MembershipTier.GOLD);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new InMemoryMemberRepository(List.of(KOFI, impostor)));

        assertEquals("Duplicate member id: m1", failure.getMessage());
    }

    @Test
    @DisplayName("Changing the original list afterwards does not change the repository")
    void shouldNotBeAffectedByLaterChangesToTheSuppliedCollection() {
        List<Member> supplied = new ArrayList<>(List.of(KOFI));
        InMemoryMemberRepository repository = new InMemoryMemberRepository(supplied);

        supplied.add(AMA);

        assertEquals(Optional.empty(), repository.findById("m2"));
    }

    @Test
    @DisplayName("A null member collection is rejected")
    void shouldRejectNullMemberCollection() {
        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> new InMemoryMemberRepository(null));

        assertEquals("members must not be null", failure.getMessage());
    }
}
