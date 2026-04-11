package org.pih.biometric.service.data.repository;

import org.pih.biometric.service.data.localdb.BiometricSubjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BiometricSubjectRepository extends JpaRepository<BiometricSubjectEntity, Long> {

    BiometricSubjectEntity findBySubjectId(String subjectId);

    BiometricSubjectEntity findByFingerprints_Id(int fingerprintId);

    @Transactional
    List<BiometricSubjectEntity> deleteBySubjectId(String subjectId);

    @Modifying
    @Transactional
    @Query("DELETE FROM BiometricSubjectEntity")
    void clearDB();

    @Query("SELECT COUNT(s) FROM BiometricSubjectEntity s")
    int getSubjectCount();

    @Query("SELECT s.id FROM BiometricSubjectEntity s")
    List<Long> getSubjectIdList();
}
