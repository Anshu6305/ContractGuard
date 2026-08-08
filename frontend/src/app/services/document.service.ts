import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DocumentDetail, DocumentSummary } from '../models/models';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly api = 'http://localhost:8080/api/documents';

  constructor(private http: HttpClient) {}

  list(): Observable<DocumentSummary[]> {
    return this.http.get<DocumentSummary[]>(this.api);
  }

  get(id: number): Observable<DocumentDetail> {
    return this.http.get<DocumentDetail>(`${this.api}/${id}`);
  }

  upload(file: File): Observable<DocumentSummary> {
    // FormData, not JSON: the backend expects multipart/form-data.
    // Do NOT set Content-Type manually here - the browser must add the
    // multipart boundary itself.
    const form = new FormData();
    form.append('file', file);
    return this.http.post<DocumentSummary>(this.api, form);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`);
  }
}
