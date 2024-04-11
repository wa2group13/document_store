package it.polito.wa2.g13.document_store.services

import it.polito.wa2.g13.document_store.aspects.DocumentError
import it.polito.wa2.g13.document_store.data.DocumentMetadata
import it.polito.wa2.g13.document_store.dtos.DocumentMetadataDTO
import it.polito.wa2.g13.document_store.dtos.UserDocumentDTO
import it.polito.wa2.g13.document_store.repositories.DocumentRepository
import it.polito.wa2.g13.document_store.util.Err
import it.polito.wa2.g13.document_store.util.Ok
import it.polito.wa2.g13.document_store.util.Result
import it.polito.wa2.g13.document_store.util.exceptions.DocumentDuplicateException
import it.polito.wa2.g13.document_store.util.exceptions.DocumentNotFoundException
import it.polito.wa2.g13.document_store.util.nullable
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class DocumentServiceImpl(private val documentRepository: DocumentRepository) : DocumentService {

    private val logger = LoggerFactory.getLogger(DocumentServiceImpl::class.java)

    override fun getDocumentByPage(pageNumber: Int, limit: Int): List<DocumentMetadataDTO> {
        return documentRepository.findAll(PageRequest.of(pageNumber, limit)).map(DocumentMetadataDTO::from).toList()
    }

    override fun getDocumentMetadataById(metadataId: Long): Result<DocumentMetadataDTO, DocumentError> {
        return documentRepository.getDocumentMetadataById(metadataId)?.let { Ok(DocumentMetadataDTO.from(it)) }
            ?: Err(DocumentError.NotFound("Document $metadataId does not exists"))
    }

    override fun getDocumentBytes(metadataId: Long): Result<String, DocumentError> {
        return documentRepository.findById(metadataId).nullable()
            ?.let { Ok(Base64.getEncoder().encodeToString(it.fileBytes.file)) }
            ?: Err(DocumentError.NotFound("Document $metadataId does not exists"))
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun saveDocument(document: UserDocumentDTO) {
        val newId = try {
            val newDocument = documentRepository.save(DocumentMetadata.from(document))
            newDocument.id
        } catch (e: DataIntegrityViolationException) {
            throw DocumentDuplicateException(e.message)
        }

        logger.info("Added new Document with Id \"$newId\".")
    }

    override fun updateDocument(metadataId: Long, document: UserDocumentDTO): Result<Unit, DocumentError> {
        val oldDocument = documentRepository.findById(metadataId).nullable()
            ?: return Err<Unit, DocumentError>(DocumentError.NotFound("Document with id \"$metadataId\" does not exists."))

        documentRepository.save(DocumentMetadata.from(document).apply {
            this.id = metadataId
            this.fileBytes.id = oldDocument.fileBytes.id
        })

        logger.info("Updated Document with Id \"$metadataId\".")
        return Ok<Unit, DocumentError>(Unit)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun deleteDocument(metadataId: Long) {
        try {
            documentRepository.deleteById(metadataId)
        } catch (e: DataRetrievalFailureException) {
            throw DocumentNotFoundException(e.message)
        }

        logger.info("Deleted Document with Id \"$metadataId\".")
    }
}