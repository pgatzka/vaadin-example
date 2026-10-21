package dev.gatzka.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** The bytes of an attachment; removed by the database when the attachment goes. */
@Entity
@Table(name = "attachment_data")
public class AttachmentData {

    @Id
    private Long attachmentId;

    @Lob
    @Column(nullable = false)
    private byte[] data;

    protected AttachmentData() {
        // JPA
    }

    public AttachmentData(long attachmentId, byte[] data) {
        this.attachmentId = attachmentId;
        this.data = data.clone();
    }

    public byte[] getData() {
        return data.clone();
    }
}
