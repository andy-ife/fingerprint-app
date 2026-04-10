package org.pih.biometric.service.data.localdb;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Entity
public class BiometricSubjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String subjectId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id_fk")
    private List<FingerprintEntity> fingerprints = new ArrayList<>();

    public BiometricSubjectEntity() {
    }

    public BiometricSubjectEntity(String subjectId) {
        this.subjectId = subjectId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public List<FingerprintEntity> getFingerprints() {
        return fingerprints;
    }

    public void setFingerprints(List<FingerprintEntity> fingerprints) {
        this.fingerprints = fingerprints;
    }

    public void addFingerprint(FingerprintEntity fingerprint) {
        this.fingerprints.add(fingerprint);
    }
}
