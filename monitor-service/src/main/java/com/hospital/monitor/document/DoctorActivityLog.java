package com.hospital.monitor.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "doctor_activity_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorActivityLog {

    @Id
    private String id;

    private String doctorId;

    private String doctorName;

    private String departmentId;

    private String action;

    private String patientId;

    private LocalDateTime recordedAt;
}
