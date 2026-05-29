package com.andyslab.biometric.service.data.localdb;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import com.andyslab.biometric.service.model.BiometricTemplateFormat;

@Entity
@Table(name = "fingerprint", schema = "public")
public class FingerprintEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Integer id;

    @Lob
    @Column(name = "template")
    private String template;

    @Lob
    @Column(name = "image")
    private String image;

    @Column(name = "type")
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "format")
    private BiometricTemplateFormat format;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "biometric_subject_id")
    private BiometricSubjectEntity biometricSubject;

    public FingerprintEntity() {
    }

    public FingerprintEntity(int id, String type, BiometricTemplateFormat format, String template) {
        this.id = id;
        this.type = type;
        this.format = format;
        this.template = template;
    }

    public FingerprintEntity(String type, BiometricTemplateFormat format, String template, String image) {
        this.type = type;
        this.format = format;
        this.template = template;
        this.image = image;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getImage() {
        if (image == null)
            return "";
        return image;
    }

    public void setImage(String image) {
        if (image == null)
            return;
        this.image = image;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BiometricTemplateFormat getFormat() {
        return format;
    }

    public void setFormat(BiometricTemplateFormat format) {
        this.format = format;
    }

    public BiometricSubjectEntity getBiometricSubject() {
        return biometricSubject;
    }

    public void setBiometricSubject(BiometricSubjectEntity subject) {
        this.biometricSubject = subject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof FingerprintEntity))
            return false;
        return id != null && id.equals(((FingerprintEntity) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
