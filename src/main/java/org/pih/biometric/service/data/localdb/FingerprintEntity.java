package org.pih.biometric.service.data.localdb;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import org.pih.biometric.service.model.BiometricTemplateFormat;

@Entity
@Table(name = "fingerprint")
public class FingerprintEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Lob
    private String template;

    private String type;

    @Enumerated(EnumType.STRING)
    private BiometricTemplateFormat format;

    public FingerprintEntity() {
    }

    public FingerprintEntity(int id, String type, BiometricTemplateFormat format, String template) {
        this.id = id;
        this.type = type;
        this.format = format;
        this.template = template;
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
}
