package com.seeker.share.document;

import java.nio.file.Path;

public record StoredDocumentAttachment(DocumentAttachmentEntity attachment, Path path) {
}
