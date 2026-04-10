package org.pih.biometric.service.data.service;

import java.util.List;
import java.util.stream.Collectors;

import org.pih.biometric.service.data.localdb.BiometricSubjectEntity;
import org.pih.biometric.service.data.localdb.FingerprintEntity;
import org.pih.biometric.service.data.repository.BiometricSubjectRepository;
import org.pih.biometric.service.model.BiometricSubject;
import org.pih.biometric.service.model.Fingerprint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BiometricSubjectService {
    private BiometricSubjectRepository subjectRepo;

    @Autowired
    public BiometricSubjectService(BiometricSubjectRepository repo) {
        this.subjectRepo = repo;
    }

    public void saveSubjectSqlite(BiometricSubject subject) {
        BiometricSubjectEntity entity = mapToEntity(subject);
        subjectRepo.save(entity);
    }

    public void saveSubjectsSqlite(List<BiometricSubject> subjects) {
        List<BiometricSubjectEntity> entities = subjects.stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
        subjectRepo.save(entities);
    }

    public List<BiometricSubject> loadSubjectsSqlite() {
        return subjectRepo.findAll().stream()
                .map(this::mapToModel)
                .collect(Collectors.toList());
    }

    public void clearDBSqlite() {
        subjectRepo.clearDB();
    }

    public int getSubjectCountSqlite() {
        return subjectRepo.getSubjectCount();
    }

    public List<Long> getSubjectIDListSqlite() {
        return subjectRepo.getSubjectIdList();
    }

    // Mappers
    private BiometricSubject mapToModel(BiometricSubjectEntity entity) {
        BiometricSubject subject = new BiometricSubject(entity.getSubjectId());
        if (entity.getFingerprints() != null) {
            for (FingerprintEntity fpEntity : entity.getFingerprints()) {
                Fingerprint fingerprint = new Fingerprint();
                fingerprint.setType(fpEntity.getType());
                fingerprint.setFormat(fpEntity.getFormat());
                fingerprint.setTemplate(fpEntity.getTemplate());
                subject.addFingerprint(fingerprint);
            }
        }
        return subject;
    }

    private BiometricSubjectEntity mapToEntity(BiometricSubject subject) {
        BiometricSubjectEntity entity = new BiometricSubjectEntity(subject.getSubjectId());
        if (subject.getFingerprints() != null) {
            for (Fingerprint fingerprint : subject.getFingerprints()) {
                FingerprintEntity fpEntity = new FingerprintEntity(
                        fingerprint.getType(), fingerprint.getFormat(), fingerprint.getTemplate());
                entity.addFingerprint(fpEntity);
            }
        }
        return entity;
    }
}
