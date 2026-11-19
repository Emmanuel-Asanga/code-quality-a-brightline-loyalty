package com.brightline.loyalty.repository;

import com.brightline.loyalty.domain.Member;
import com.brightline.loyalty.domain.MembershipTier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A {@link MemberRepository} backed by an immutable in-memory map. */
public class InMemoryMemberRepository implements MemberRepository {

    private final Map<String, Member> membersById;

    public InMemoryMemberRepository(Collection<Member> members) {
        Objects.requireNonNull(members, "members must not be null");
        Map<String, Member> indexed = new HashMap<>();
        for (Member member : members) {
            if (indexed.putIfAbsent(member.id(), member) != null) {
                throw new IllegalArgumentException("Duplicate member id: " + member.id());
            }
        }
        this.membersById = Collections.unmodifiableMap(indexed);
    }

    /** The three members the original application shipped with, used by the demo. */
    public static InMemoryMemberRepository withSampleMembers() {
        return new InMemoryMemberRepository(List.of(
                new Member("m1", "Kofi Mensah", MembershipTier.STANDARD),
                new Member("m2", "Ama Owusu", MembershipTier.GOLD),
                new Member("m3", "Yaa Boateng", MembershipTier.SILVER)));
    }

    @Override
    public Optional<Member> findById(String id) {
        return Optional.ofNullable(membersById.get(id));
    }
}
