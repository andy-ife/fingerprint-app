package com.andyslab.biometric.service.data.localdb;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "biometric_subject")
public class BiometricSubjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "subject_id")
    private String subjectId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "biometric_subject_id")
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
