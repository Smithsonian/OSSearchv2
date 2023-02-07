package edu.si.ossearch.nutch.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@ToString
@Entity
@Table(name = "links")
public class Links implements Serializable {
    private static final long serialVersionUID = 8908148079243041426L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ElementCollection(fetch = FetchType.LAZY)
    @Column(columnDefinition = "TEXT")
    private Set<String> inlinks = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @Column(columnDefinition = "TEXT")
    private Set<String> outlinks = new HashSet<>();
}
