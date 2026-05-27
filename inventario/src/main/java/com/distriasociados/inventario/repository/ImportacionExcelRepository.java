package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.ImportacionExcel;
import com.distriasociados.inventario.entity.ImportacionExcel.TipoImportacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ImportacionExcelRepository extends JpaRepository<ImportacionExcel, Long> {

    List<ImportacionExcel> findByTipo(TipoImportacion tipo);
    List<ImportacionExcel> findByOrderByCreadoEnDesc();
}