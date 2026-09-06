package com.fiap.sast.persistence;
import com.fasterxml.jackson.annotation.JsonIgnore; import jakarta.persistence.*; import java.util.UUID;
@Entity @Table(name="findings") public class Finding { @Id public UUID id=UUID.randomUUID(); @JsonIgnore @ManyToOne @JoinColumn(name="analysis_id") public Analysis analysis; public String ruleId,title,severity,cwe,description,fileName; public int line; @Column(name="column_number") public int column; public String snippet; }
