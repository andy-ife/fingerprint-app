package org.pih.biometric.service.data.repository;

import org.pih.biometric.service.data.sqlite.FingerprintEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FingerprintRepository extends JpaRepository<FingerprintEntity, Long> {

    // 3. Clearing the fingerprint database
    @Modifying
    @Transactional
    @Query("DELETE FROM FingerprintEntity")
    void clearFPDB();

    // 4. Getting the number of registered fingerprints
    @Query("SELECT COUNT(f) FROM FingerprintEntity f")
    int getFPCount();

    // 5. Getting the list of template IDs
    @Query("SELECT f.id FROM FingerprintEntity f")
    List<Long> getIDList();
}
