package com.dxc.monitoring.repository;

import com.dxc.monitoring.entity.MenuItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByEnabledTrueOrderByDisplayOrderAscIdAsc();

    boolean existsByMenuKeyIgnoreCase(String menuKey);
    boolean existsByParentId(Long parentId);
    boolean existsByMenuKeyIgnoreCaseAndIdNot(String menuKey, Long id);

    @Query(value = """
            select m from MenuItem m
            where :search = ''
               or lower(m.menuKey) like concat('%', :search, '%')
               or lower(m.menuName) like concat('%', :search, '%')
               or lower(coalesce(m.url, '')) like concat('%', :search, '%')
               or lower(coalesce(m.requiredPermission, '')) like concat('%', :search, '%')
            """,
            countQuery = """
            select count(m) from MenuItem m
            where :search = ''
               or lower(m.menuKey) like concat('%', :search, '%')
               or lower(m.menuName) like concat('%', :search, '%')
               or lower(coalesce(m.url, '')) like concat('%', :search, '%')
               or lower(coalesce(m.requiredPermission, '')) like concat('%', :search, '%')
            """)
    Page<MenuItem> search(@Param("search") String search, Pageable pageable);
}
