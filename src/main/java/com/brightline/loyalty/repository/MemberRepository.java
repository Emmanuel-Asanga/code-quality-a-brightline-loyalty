package com.brightline.loyalty.repository;

import com.brightline.loyalty.domain.Member;
import com.brightline.loyalty.exception.MemberRepositoryException;
import java.util.Optional;

/** Source of loyalty members. */
public interface MemberRepository {

    /**
     * Finds a member by id.
     *
     * @param id the member id
     * @return the member, or {@link Optional#empty()} if no such member exists
     * @throws MemberRepositoryException if the lookup itself fails (an absent member is not a
     *     failure)
     */
    Optional<Member> findById(String id);
}
