package org.pih.biometric.service.data.service;

import java.util.List;
import java.util.stream.Collectors;

import org.pih.biometric.service.data.localdb.FingerprintEntity;
import org.pih.biometric.service.data.repository.FingerprintRepository;
import org.pih.biometric.service.model.Fingerprint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FingerprintService {
    private FingerprintRepository fingerprintRepo;

    @Autowired
    public FingerprintService(FingerprintRepository repo) {
        this.fingerprintRepo = repo;
    }

    // Backup SQLite db service
    public void saveFPDBSqlite(List<Fingerprint> fingerprints) {
        List<FingerprintEntity> entities = fingerprints.stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
        fingerprintRepo.save(entities);
    }

    public List<Fingerprint> loadFPDBSqlite() {
        return fingerprintRepo.findAll().stream()
                .map(this::mapToModel)
                .collect(Collectors.toList());
    }

    public void clearFPDBSqlite() {
        fingerprintRepo.clearFPDB();
    }

    public int getFPCountSqlite() {
        return fingerprintRepo.getFPCount();
    }

    public List<Long> getIDListSqlite() {
        return fingerprintRepo.getIDList();
    }

    // Mappers
    private Fingerprint mapToModel(FingerprintEntity fingerprintEntity) {
        return new Fingerprint(
                fingerprintEntity.getType(), fingerprintEntity.getFormat(), fingerprintEntity.getTemplate());
    }

    private FingerprintEntity mapToEntity(Fingerprint fingerprint) {
        return new FingerprintEntity(
                fingerprint.getType(), fingerprint.getFormat(), fingerprint.getTemplate());
    }
}
