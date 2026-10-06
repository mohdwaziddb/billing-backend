package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findAllByEmailIgnoreCase(String email);

    List<User> findAllByUsernameIgnoreCase(String username);

    List<User> findAllByMobileNumber(String mobileNumber);

    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByMobileNumber(String mobileNumber);
    List<User> findAllByOrderByCreatedAtDesc();
    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select u from User u where (:company is null or 1=1) and lower(u.username) = lower(:username)")
    Optional<User> findByCompanyAndUsernameIgnoreCase(@Param("company") Company company, @Param("username") String username);
    @Query("select u from User u where (:company is null or 1=1) and lower(u.email) = lower(:email)")
    Optional<User> findByCompanyAndEmailIgnoreCase(@Param("company") Company company, @Param("email") String email);
    @Query("select u from User u where (:company is null or 1=1) and u.mobileNumber = :mobileNumber")
    Optional<User> findByCompanyAndMobileNumber(@Param("company") Company company, @Param("mobileNumber") String mobileNumber);
    @Query("select u from User u where (:company is null or 1=1) order by u.createdAt desc")
    List<User> findByCompanyOrderByCreatedAtDesc(@Param("company") Company company);
    @Query("select u from User u where (:company is null or 1=1) order by u.createdAt desc")
    Page<User> findByCompanyOrderByCreatedAtDesc(@Param("company") Company company, Pageable pageable);
    @Query("select u from User u where u.id = :id and (:company is null or 1=1)")
    Optional<User> findByIdAndCompany(@Param("id") Long id, @Param("company") Company company);

    @Query("""
            select u from User u
            where (:company is null or 1=1) and (:name is null or lower(u.fullName) like lower(concat('%', :name, '%')))
              and (:username is null or lower(u.username) like lower(concat('%', :username, '%')))
              and (:mobileNumber is null or u.mobileNumber like concat('%', :mobileNumber, '%'))
              and (:email is null or lower(u.email) like lower(concat('%', :email, '%')))
              and (:search is null
                or lower(u.fullName) like lower(concat('%', :search, '%'))
                or lower(u.username) like lower(concat('%', :search, '%'))
                or u.mobileNumber like concat('%', :search, '%')
                or lower(u.email) like lower(concat('%', :search, '%')))
              and (:role is null or u.role = :role)
              and (:active is null or u.active = :active)
            order by u.createdAt desc
            """)
    Page<User> searchCompanyUsers(@Param("company") Company company,
                                  @Param("name") String name,
                                  @Param("username") String username,
                                  @Param("mobileNumber") String mobileNumber,
                                  @Param("email") String email,
                                  @Param("search") String search,
                                  @Param("role") RoleName role,
                                  @Param("active") Boolean active,
                                  Pageable pageable);
}
