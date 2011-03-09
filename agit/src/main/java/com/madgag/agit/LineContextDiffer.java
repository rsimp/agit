import static org.eclipse.jgit.diff.RawTextComparator.DEFAULT;
	private int bigFileThreshold = 1 * 1024 * 1024;
	private final ObjectReader objectReader;
	public LineContextDiffer(ObjectReader objectReader) {
		this.objectReader = objectReader;
				aRaw = open(objectReader, ent.getOldMode(), ent.getOldId());
				bRaw = open(objectReader, ent.getNewMode(), ent.getNewId());
				// objectReader.release();
				RawText a = new RawText(aRaw);
				RawText b = new RawText(bRaw);
				return formatEdits(a, b, MyersDiff.INSTANCE.diff(DEFAULT, a, b));
		if (id.isComplete()) {
				id = objectReader.abbreviate(id.toObjectId(), abbreviationLength);
				// reader.release();