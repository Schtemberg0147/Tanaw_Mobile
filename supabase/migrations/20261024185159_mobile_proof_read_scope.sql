-- Document INSERT permission alone must not let a driver attach a victim object's
-- path to an otherwise authorized shipment. Bind path, delivery, shipment and owner.
ALTER POLICY "active accounts read recorded authorized proof" ON storage.objects
USING (
  bucket_id='delivery-documents'
  AND EXISTS(SELECT 1 FROM public.users u WHERE u.id=(SELECT auth.uid()) AND u.is_active)
  AND EXISTS(
    SELECT 1 FROM public.delivery_documents d
    JOIN public.deliveries delivery ON delivery.id=d.delivery_id AND delivery.shipment_id=d.shipment_id
    WHERE d.storage_path=storage.objects.name
      AND storage.objects.owner=d.uploaded_by
      AND (storage.foldername(storage.objects.name))[1]=delivery.id::text
      AND ((d.kind::text='proof_of_delivery' AND storage.filename(storage.objects.name) LIKE 'pod-photo-%')
        OR (d.kind::text='signature' AND storage.filename(storage.objects.name) LIKE 'signature-%'))
  )
);
