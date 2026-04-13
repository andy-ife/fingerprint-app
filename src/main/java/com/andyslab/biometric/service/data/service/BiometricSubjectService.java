package com.andyslab.biometric.service.data.service;

import java.util.List;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.andyslab.biometric.service.data.localdb.BiometricSubjectEntity;
import com.andyslab.biometric.service.data.localdb.FingerprintEntity;
import com.andyslab.biometric.service.data.repository.BiometricSubjectRepository;
import com.andyslab.biometric.service.model.BiometricSubject;
import com.andyslab.biometric.service.model.Fingerprint;

@Service
public class BiometricSubjectService {
    private BiometricSubjectRepository subjectRepo;

    @Autowired
    public BiometricSubjectService(BiometricSubjectRepository repo) {
        this.subjectRepo = repo;
    }

    public BiometricSubject saveSubject(BiometricSubject subject) {
        BiometricSubjectEntity entity = mapToEntity(subject);
        entity = subjectRepo.save(entity);
        return mapToModel(entity);
    }

    public List<BiometricSubject> saveSubjects(List<BiometricSubject> subjects) {
        List<BiometricSubjectEntity> entities = subjects.stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
        entities = subjectRepo.saveAll(entities);
        return entities.stream().map(this::mapToModel).collect(Collectors.toList());
    }

    public List<BiometricSubject> loadSubjects() {
        return subjectRepo.findAll().stream()
                .map(this::mapToModel)
                .collect(Collectors.toList());
    }

    public BiometricSubject getSubjectByFingerprintId(int fingerprintId) {
        BiometricSubjectEntity entity = subjectRepo.findByFingerprints_Id(fingerprintId);
        if (entity != null) {
            return mapToModel(entity);
        }
        return null;
    }

    public BiometricSubject findSubjectBySubjectId(String subjectId) {
        BiometricSubjectEntity entity = subjectRepo.findBySubjectId(subjectId);
        return mapToModel(entity);
    }

    @Transactional
    public void removeSubject(BiometricSubject subject) {
        BiometricSubjectEntity entity = subjectRepo.findBySubjectId(subject.getSubjectId());
        if (entity != null) {
            subjectRepo.delete(entity);
        }
    }

    @Transactional
    public BiometricSubject deleteBySubjectId(String subjectId) {
        List<BiometricSubjectEntity> deletedEntities = subjectRepo.deleteBySubjectId(subjectId);
        if (deletedEntities != null && !deletedEntities.isEmpty()) {
            return mapToModel(deletedEntities.get(0));
        }
        return null;
    }

    @Transactional
    public BiometricSubject updateSubject(BiometricSubject subject) {
        BiometricSubjectEntity entity = subjectRepo.findBySubjectId(subject.getSubjectId());
        if (entity != null) {
            // Clear existing fingerprints to let orphanRemoval clean them up
            entity.getFingerprints().clear();

            // Add the updated/new fingerprints
            if (subject.getFingerprints() != null) {
                for (Fingerprint fingerprint : subject.getFingerprints()) {
                    FingerprintEntity fpEntity = new FingerprintEntity(
                            fingerprint.getId(), fingerprint.getType(), fingerprint.getFormat(),
                            fingerprint.getTemplate());
                    entity.addFingerprint(fpEntity);
                }
            }

            // Save handles updates for existing entities automatically in JPA
            entity = subjectRepo.save(entity);
            return mapToModel(entity);
        }
        return null;
    }

    public void clearDB() {
        subjectRepo.clearDB();
    }

    public int getSubjectCount() {
        return subjectRepo.getSubjectCount();
    }

    public List<Long> getSubjectIDList() {
        return subjectRepo.getSubjectIdList();
    }

    // Mappers
    private BiometricSubject mapToModel(BiometricSubjectEntity entity) {
        BiometricSubject subject = new BiometricSubject(entity.getSubjectId());
        if (entity.getFingerprints() != null) {
            for (FingerprintEntity fpEntity : entity.getFingerprints()) {
                Fingerprint fingerprint = new Fingerprint();
                fingerprint.setId(fpEntity.getId());
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
                        fingerprint.getId(), fingerprint.getType(), fingerprint.getFormat(), fingerprint.getTemplate());
                entity.addFingerprint(fpEntity);
            }
        }
        return entity;
    }
}
