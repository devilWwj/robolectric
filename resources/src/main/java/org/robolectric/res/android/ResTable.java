package org.robolectric.res.android;

import static org.robolectric.res.android.Errors.BAD_INDEX;
import static org.robolectric.res.android.Errors.BAD_TYPE;
import static org.robolectric.res.android.Errors.BAD_VALUE;
import static org.robolectric.res.android.Errors.NO_ERROR;
import static org.robolectric.res.android.Errors.NO_MEMORY;
import static org.robolectric.res.android.Errors.UNKNOWN_ERROR;
import static org.robolectric.res.android.Util.ALOGE;
import static org.robolectric.res.android.Util.ALOGI;
import static org.robolectric.res.android.Util.ALOGW;
import static org.robolectric.res.android.Util.dtohl;
import static org.robolectric.res.android.Util.dtohs;
import static org.robolectric.res.android.Util.htodl;
import static org.robolectric.res.android.Util.isTruthy;

import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.robolectric.util.Strings;

// transliterated from https://android.googlesource.com/platform/frameworks/base/+/android-7.1.1_r13/libs/androidfw/ResourceTypes.cpp
//   and https://android.googlesource.com/platform/frameworks/base/+/android-7.1.1_r13/include/androidfw/ResourceTypes.h
public class ResTable {

  private static final int IDMAP_MAGIC             = 0x504D4449;
  private static final int IDMAP_CURRENT_VERSION   = 0x00000001;

  static final int APP_PACKAGE_ID      = 0x7f;
  static final int SYS_PACKAGE_ID      = 0x01;

  private static final boolean kDebugStringPoolNoisy = false;
  private static final boolean kDebugXMLNoisy = false;
  private static final boolean kDebugTableNoisy = false;
  private static final boolean kDebugTableGetEntry = false;
  static final boolean kDebugTableSuperNoisy = false;
  private static final boolean kDebugLoadTableNoisy = false;
  private static final boolean kDebugLoadTableSuperNoisy = false;
  private static final boolean kDebugTableTheme = false;
  private static final boolean kDebugResXMLTree = false;
  private static final boolean kDebugLibNoisy = false;

  private static final Object NULL = null;
  public static final bag_set SENTINEL_BAG_SET = new bag_set(1);

  final Semaphore mLock = new Semaphore(1);

  // Mutex that controls access to the list of pre-filtered configurations
  // to check when looking up entries.
  // When iterating over a bag, the mLock mutex is locked. While mLock is locked,
  // we do resource lookups.
  // Mutex is not reentrant, so we must use a different lock than mLock.
  final Object               mFilteredConfigLock = new Object();

  // type defined in Errors
  int mError;

  ResTableConfig             mParams;

  // Array of all resource tables.
  List<Header>             mHeaders = new ArrayList<>();

  // Array of packages in all resource tables.
  Map<Integer, PackageGroup> mPackageGroups = new HashMap<>();

  // Mapping from resource package IDs to indices into the internal
  // package array.
  byte[]                     mPackageMap = new byte[256];

  byte                     mNextPackageId;
  private ResTableConfig parameters;

  static boolean Res_CHECKID(int resid) { return ((resid&0xFFFF0000) != 0);}
  static int Res_GETPACKAGE(int id) {
    return ((id>>24)-1);
  }
  static int Res_GETTYPE(int id) {
    return (((id>>16)&0xFF)-1);
  }
  static int Res_GETENTRY(int id) {
    return (id&0xFFFF);
  }
  static int Res_MAKEARRAY(int entry) { return (0x02000000 | (entry&0xFFFF)); }
  static boolean Res_INTERNALID(int resid) { return ((resid&0xFFFF0000) != 0 && (resid&0xFF0000) == 0); }

  int getResourcePackageIndex(int resID)
  {
    return Res_GETPACKAGE(resID) + 1;
    //return mPackageMap[Res_GETPACKAGE(resID)+1]-1;
  }

  public void add(InputStream is, int cookie) throws IOException {
    byte[] buf = ByteStreams.toByteArray(is);
    ByteBuffer buffer = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
    Chunk.read(buffer, this, cookie);
  }

//  Errors add(final Object data, int size, final int cookie, boolean copyData) {
//    return addInternal(data, size, NULL, 0, false, cookie, copyData);
//  }
//
//  Errors add(final Object data, int size, final Object idmapData, int idmapDataSize,
//        final int cookie, boolean copyData, boolean appAsLib) {
//    return addInternal(data, size, idmapData, idmapDataSize, appAsLib, cookie, copyData);
//  }
//
//  Errors add(Asset asset, final int cookie, boolean copyData) {
//    final Object data = asset.getBuffer(true);
//    if (data == NULL) {
//      ALOGW("Unable to get buffer of resource asset file");
//      return UNKNOWN_ERROR;
//    }
//
//    return addInternal(data, static_cast<int>(asset.getLength()), NULL, false, 0, cookie,
//        copyData);
//  }
//
//  Errors add(
//      Asset asset, Asset idmapAsset, final int cookie, boolean copyData,
//      boolean appAsLib, boolean isSystemAsset) {
//    final Object data = asset.getBuffer(true);
//    if (data == NULL) {
//      ALOGW("Unable to get buffer of resource asset file");
//      return UNKNOWN_ERROR;
//    }
//
//    int idmapSize = 0;
//    final Object idmapData = NULL;
//    if (idmapAsset != NULL) {
//      idmapData = idmapAsset.getBuffer(true);
//      if (idmapData == NULL) {
//        ALOGW("Unable to get buffer of idmap asset file");
//        return UNKNOWN_ERROR;
//      }
//      idmapSize = static_cast<int>(idmapAsset.getLength());
//    }
//
//    return addInternal(data, static_cast<int>(asset.getLength()),
//        idmapData, idmapSize, appAsLib, cookie, copyData, isSystemAsset);
//  }
//
//  Errors add(ResTable* src, boolean isSystemAsset)
//  {
//    mError = src.mError;
//
//    for (int i=0; i < src.mHeaders.size(); i++) {
//      mHeaders.add(src.mHeaders[i]);
//    }
//
//    for (int i=0; i < src.mPackageGroups.size(); i++) {
//      PackageGroup* srcPg = src.mPackageGroups[i];
//      PackageGroup* pg = new PackageGroup(this, srcPg.name, srcPg.id,
//          false /* appAsLib */, isSystemAsset || srcPg.isSystemAsset);
//      for (int j=0; j<srcPg.packages.size(); j++) {
//        pg.packages.add(srcPg.packages[j]);
//      }
//
//      for (int j = 0; j < srcPg.types.size(); j++) {
//        if (srcPg.types[j].isEmpty()) {
//          continue;
//        }
//
//        TypeList& typeList = pg.types.editItemAt(j);
//        typeList.appendVector(srcPg.types[j]);
//      }
//      pg.dynamicRefTable.addMappings(srcPg.dynamicRefTable);
//      pg.largestTypeId = max(pg.largestTypeId, srcPg.largestTypeId);
//      mPackageGroups.add(pg);
//    }
//
//    memcpy(mPackageMap, src.mPackageMap, sizeof(mPackageMap));
//
//    return mError;
//  }
//
//  Errors addEmpty(final int cookie) {
//    Header* header = new Header(this);
//    header.index = mHeaders.size();
//    header.cookie = cookie;
//    header.values.setToEmpty();
//    header.ownedData = calloc(1, sizeof(ResTable_header));
//
//    ResTable_header* resHeader = (ResTable_header*) header.ownedData;
//    resHeader.header.type = RES_TABLE_TYPE;
//    resHeader.header.headerSize = sizeof(ResTable_header);
//    resHeader.header.size = sizeof(ResTable_header);
//
//    header.header = (final ResTable_header*) resHeader;
//    mHeaders.add(header);
//    return (mError=NO_ERROR);
//  }
//
//  Errors addInternal(final Object data, int dataSize, final Object idmapData, int idmapDataSize,
//      boolean appAsLib, final int cookie, boolean copyData, boolean isSystemAsset)
//  {
//    if (!data) {
//      return NO_ERROR;
//    }
//
//    if (dataSize < sizeof(ResTable_header)) {
//      ALOGE("Invalid data. Size(%d) is smaller than a ResTable_header(%d).",
//          (int) dataSize, (int) sizeof(ResTable_header));
//      return UNKNOWN_ERROR;
//    }
//
//    Header header = new Header(this);
//    header.index = mHeaders.size();
//    header.cookie = cookie;
//    if (idmapData != NULL) {
//      header.resourceIDMap = new int[idmapDataSize / 4];
//      if (header.resourceIDMap == NULL) {
////        delete header;
//        return (mError = NO_MEMORY);
//      }
//      memcpy(header.resourceIDMap, idmapData, idmapDataSize);
//      header.resourceIDMapSize = idmapDataSize;
//    }
//    mHeaders.add(header);
//
//    final boolean notDeviceEndian = htods(0xf0) != 0xf0;
//
//    if (kDebugLoadTableNoisy) {
//      ALOGV("Adding resources to ResTable: data=%p, size=%zu, cookie=%d, copy=%d "
//          "idmap=%p\n", data, dataSize, cookie, copyData, idmapData);
//    }
//
//    if (copyData || notDeviceEndian) {
//      header.ownedData = malloc(dataSize);
//      if (header.ownedData == NULL) {
//        return (mError=NO_MEMORY);
//      }
//      memcpy(header.ownedData, data, dataSize);
//      data = header.ownedData;
//    }
//
//    header.header = (final ResTable_header*)data;
//    header.size = dtohl(header.header.header.size);
//    if (kDebugLoadTableSuperNoisy) {
//      ALOGI("Got size %zu, again size 0x%x, raw size 0x%x\n", header.size,
//          dtohl(header.header.header.size), header.header.header.size);
//    }
//    if (kDebugLoadTableNoisy) {
//      ALOGV("Loading ResTable @%p:\n", header.header);
//    }
//    if (dtohs(header.header.header.headerSize) > header.size
//        || header.size > dataSize) {
//      ALOGW("Bad resource table: header size 0x%x or total size 0x%x is larger than data size 0x%x\n",
//          (int)dtohs(header.header.header.headerSize),
//          (int)header.size, (int)dataSize);
//      return (mError=BAD_TYPE);
//    }
//    if (((dtohs(header.header.header.headerSize)|header.size)&0x3) != 0) {
//      ALOGW("Bad resource table: header size 0x%x or total size 0x%x is not on an integer boundary\n",
//          (int)dtohs(header.header.header.headerSize),
//          (int)header.size);
//      return (mError=BAD_TYPE);
//    }
//    header.dataEnd = ((final uint8_t*)header.header) + header.size;
//
//    // Iterate through all chunks.
//    int curPackage = 0;
//
//    final ResChunk_header* chunk =
//      (final ResChunk_header*)(((final uint8_t*)header.header)
//    + dtohs(header.header.header.headerSize));
//    while (((final uint8_t*)chunk) <= (header.dataEnd-sizeof(ResChunk_header)) &&
//      ((final uint8_t*)chunk) <= (header.dataEnd-dtohl(chunk.size))) {
//    Errors err = validate_chunk(chunk, sizeof(ResChunk_header), header.dataEnd, "ResTable");
//    if (err != NO_ERROR) {
//      return (mError=err);
//    }
//    if (kDebugTableNoisy) {
//      ALOGV("Chunk: type=0x%x, headerSize=0x%x, size=0x%x, pos=%p\n",
//          dtohs(chunk.type), dtohs(chunk.headerSize), dtohl(chunk.size),
//          (Object)(((final uint8_t*)chunk) - ((final uint8_t*)header.header)));
//    }
//        final int csize = dtohl(chunk.size);
//        final int ctype = dtohs(chunk.type);
//    if (ctype == RES_STRING_POOL_TYPE) {
//      if (header.values.getError() != NO_ERROR) {
//        // Only use the first string chunk; ignore any others that
//        // may appear.
//        Errors err = header.values.setTo(chunk, csize);
//        if (err != NO_ERROR) {
//          return (mError=err);
//        }
//      } else {
//        ALOGW("Multiple string chunks found in resource table.");
//      }
//    } else if (ctype == RES_TABLE_PACKAGE_TYPE) {
//      if (curPackage >= dtohl(header.header.packageCount)) {
//        ALOGW("More package chunks were found than the %d declared in the header.",
//            dtohl(header.header.packageCount));
//        return (mError=BAD_TYPE);
//      }
//
//      if (parsePackage(
//          (ResTable_package*)chunk, header, appAsLib, isSystemAsset) != NO_ERROR) {
//        return mError;
//      }
//      curPackage++;
//    } else {
//      ALOGW("Unknown chunk type 0x%x in table at %p.\n",
//          ctype,
//          (Object)(((final uint8_t*)chunk) - ((final uint8_t*)header.header)));
//    }
//    chunk = (final ResChunk_header*)
//    (((final uint8_t*)chunk) + csize);
//  }
//
//    if (curPackage < dtohl(header.header.packageCount)) {
//      ALOGW("Fewer package chunks (%d) were found than the %d declared in the header.",
//          (int)curPackage, dtohl(header.header.packageCount));
//      return (mError=BAD_TYPE);
//    }
//    mError = header.values.getError();
//    if (mError != NO_ERROR) {
//      ALOGW("No string values found in resource table!");
//    }
//
//    if (kDebugTableNoisy) {
//      ALOGV("Returning from add with mError=%d\n", mError);
//    }
//    return mError;
//  }

  public final int getResource(int resID, Ref<ResValue> outValue, boolean mayBeBag, int density,
      Ref<Integer> outSpecFlags, Ref<ResTableConfig> outConfig)
  {
    if (mError != NO_ERROR) {
      return mError;
    }
    final int p = getResourcePackageIndex(resID);
    final int t = Res_GETTYPE(resID);
    final int e = Res_GETENTRY(resID);
    if (p < 0) {
      if (Res_GETPACKAGE(resID)+1 == 0) {
        ALOGW("No package identifier when getting value for resource number 0x%08x", resID);
      } else {
        ALOGW("No known package when getting value for resource number 0x%08x", resID);
      }
      return BAD_INDEX;
    }

    if (t < 0) {
      ALOGW("No type identifier when getting value for resource number 0x%08x", resID);
      return BAD_INDEX;
    }
    final PackageGroup grp = mPackageGroups.get(p);
    if (grp == NULL) {
      ALOGW("Bad identifier when getting value for resource number 0x%08x", resID);
      return BAD_INDEX;
    }
    // Allow overriding density
    ResTableConfig desiredConfig = mParams;
    if (density > 0) {
      desiredConfig.density = density;
    }
    Ref<Entry> entry = new Ref<>(new Entry());
    int err = getEntry(grp, t, e, desiredConfig, entry);
    if (err != NO_ERROR) {
      // Only log the failure when we're not running on the host as
      // part of a tool. The caller will do its own logging.
      return err;
    }

    if ((entry.get().entry.flags & ResTableEntry.FLAG_COMPLEX) != 0) {
      if (!mayBeBag) {
        ALOGW("Requesting resource 0x%08x failed because it is complex\n", resID);
      }
      return BAD_VALUE;
    }

    ResValue value = entry.get().entry.value;
    //outValue.get().size = dtohs(value.size);
    //outValue.get().res0 = value.res0;
    outValue.set(new ResValue(value));
    //outValue.get().data = value.data;
    // The reference may be pointing to a resource in a shared library. These
    // references have build-time generated package IDs. These ids may not match
    // the actual package IDs of the corresponding packages in this ResTable.
    // We need to fix the package ID based on a mapping.
    if (grp.dynamicRefTable.lookupResourceValue(outValue.get()) != NO_ERROR) {
      ALOGW("Failed to resolve referenced package: 0x%08x", outValue.get().data);
      return BAD_VALUE;
    }
//    if (kDebugTableNoisy) {
//      size_t len;
//      printf("Found value: pkg=%zu, type=%d, str=%s, int=%d\n",
//          entry.package.header.index,
//          outValue.dataType,
//          outValue.dataType == Res_value::TYPE_STRING ?
//              String8(entry.package.header.values.stringAt(outValue.data, &len)).string() :
//      "",
//          outValue.data);
//    }
    if (outSpecFlags != null) {
        outSpecFlags.set(entry.get().specFlags);
    }
    if (outConfig != null) {
        outConfig.set(entry.get().config);
    }
    return entry.get()._package_.header.index;
  }

  public final int resolveReference(Ref<ResValue> value, int blockIndex,
      Ref<Integer> outLastRef) {
    return resolveReference(value, blockIndex, outLastRef, null, null);
  }

  public final int resolveReference(Ref<ResValue> value, int blockIndex,
      Ref<Integer> outLastRef, Ref<Integer> inoutTypeSpecFlags) {
    return resolveReference(value, blockIndex, outLastRef, inoutTypeSpecFlags, null);
  }

  public final int resolveReference(Ref<ResValue> value, int blockIndex,
      Ref<Integer> outLastRef, Ref<Integer> inoutTypeSpecFlags,
      Ref<ResTableConfig> outConfig)
  {
    int count=0;
    while (blockIndex >= 0 && value.get().dataType == DataType.REFERENCE.code()
        && value.get().data != 0 && count < 20) {
      if (outLastRef != null) {
        outLastRef.set(value.get().data);
      }
      Ref<Integer> newFlags = new Ref<>(0);
      final int newIndex = getResource(value.get().data, value, true, 0,
          newFlags, outConfig);
      if (newIndex == BAD_INDEX) {
        return BAD_INDEX;
      }
      if (kDebugTableTheme) {
        ALOGI("Resolving reference 0x%x: newIndex=%d, type=0x%x, data=0x%x\n",
            value.get().data, (int)newIndex, (int)value.get().dataType, value.get().data);
      }
      //printf("Getting reference 0x%08x: newIndex=%d\n", value.data, newIndex);
      if (inoutTypeSpecFlags != null) {
        inoutTypeSpecFlags.set(inoutTypeSpecFlags.get() | newFlags.get());
      }
      if (newIndex < 0) {
        // This can fail if the resource being referenced is a style...
        // in this case, just return the reference, and expect the
        // caller to deal with.
        return blockIndex;
      }
      blockIndex = newIndex;
      count++;
    }
    return blockIndex;
  }

  private int getEntry(
      final PackageGroup packageGroup, int typeIndex, int entryIndex,
      final ResTableConfig config,
      Ref<Entry> outEntryRef)
  {
    final List<Type> typeList = packageGroup.types.get(typeIndex);
    if (typeList.isEmpty()) {
      Util.ALOGV("Skipping entry type index 0x%02x because type is NULL!\n", typeIndex);
      return BAD_TYPE;
    }

    ResTableType bestType = null;
    int bestOffset = ResTableType.NO_ENTRY;
    ResTableEntry bestEntry = null;
    Package bestPackage = null;
    int specFlags = 0;
    byte actualTypeIndex = (byte) typeIndex;
    ResTableConfig bestConfig = null;
//    memset(&bestConfig, 0, sizeof(bestConfig));

    // Iterate over the Types of each package.
    final int typeCount = typeList.size();
    for (int i = 0; i < typeCount; i++) {
      final Type typeSpec = typeList.get(i);

      int realEntryIndex = entryIndex;
      int realTypeIndex = typeIndex;
      boolean currentTypeIsOverlay = false;

      // Runtime overlay packages provide a mapping of app resource
      // ID to package resource ID.
      if (typeSpec.idmapEntries.hasEntries()) {
        Ref<Short> overlayEntryIndex = new Ref<>((short) 0);
        if (typeSpec.idmapEntries.lookup(entryIndex, overlayEntryIndex) != NO_ERROR) {
          // No such mapping exists
          continue;
        }
        realEntryIndex = overlayEntryIndex.get();
        realTypeIndex = typeSpec.idmapEntries.overlayTypeId() - 1;
        currentTypeIsOverlay = true;
      }

      if (((int) realEntryIndex) >= typeSpec.entryCount) {
        ALOGW("For resource 0x%08x, entry index(%d) is beyond type entryCount(%d)",
            Res_MAKEID(packageGroup.id - 1, typeIndex, entryIndex),
            entryIndex, ((int) typeSpec.entryCount));
        // We should normally abort here, but some legacy apps declare
        // resources in the 'android' package (old bug in AAPT).
        continue;
      }

      // Aggregate all the flags for each package that defines this entry.
      if (typeSpec.typeSpecFlags != null) {
        specFlags |= dtohl(typeSpec.typeSpecFlags[realEntryIndex]);
      } else {
        specFlags = -1;
      }

      List<ResTableType> candidateConfigs = typeSpec.configs;

      List<ResTableType> filteredConfigs;
//      if (isTruthy(config) && Objects.equals(mParams, config)) {
//        // Grab the lock first so we can safely get the current filtered list.
//        synchronized (mFilteredConfigLock) {
//          // This configuration is equal to the one we have previously cached for,
//          // so use the filtered configs.
//
//          final TypeCacheEntry cacheEntry = packageGroup.typeCacheEntries.get(typeIndex);
//          if (i < cacheEntry.filteredConfigs.size()) {
//            if (isTruthy(cacheEntry.filteredConfigs.get(i))) {
//              // Grab a reference to the shared_ptr so it doesn't get destroyed while
//              // going through this list.
//              filteredConfigs = cacheEntry.filteredConfigs.get(i);
//
//              // Use this filtered list.
//              candidateConfigs = filteredConfigs;
//            }
//          }
//        }
//      }

      final int numConfigs = candidateConfigs.size();
      for (int c = 0; c < numConfigs; c++) {
        final ResTableType thisType = candidateConfigs.get(c);
        if (thisType == NULL) {
          continue;
        }

        final ResTableConfig thisConfig;
//        thisConfig.copyFromDtoH(thisType.config);
        thisConfig = ResTableConfig.fromDtoH(thisType.config);

        // Check to make sure this one is valid for the current parameters.
        if (config != NULL && !thisConfig.match(config)) {
          continue;
        }

        bestEntry = thisType.entries.get(realEntryIndex);
        if (bestEntry == null) {
          // There is no entry for this index and configuration.
          continue;
        }

        // Check if there is the desired entry in this type.
//        final uint* final eindex = reinterpret_cast<final uint*>(
//            reinterpret_cast<final uint8_t*>(thisType) + dtohs(thisType.header.headerSize));
//        final int[] eindex = thisType.eindex(dtohs(thisType.header.headerSize));

//        int thisOffset = dtohl(eindex[realEntryIndex]);
//        if (thisOffset == ResTableType.NO_ENTRY) {
//          // There is no entry for this index and configuration.
//          continue;
//        }

        if (bestType != NULL) {
          // Check if this one is less specific than the last found.  If so,
          // we will skip it.  We check starting with things we most care
          // about to those we least care about.
          if (!thisConfig.isBetterThan(bestConfig, config)) {
            if (!currentTypeIsOverlay || thisConfig.compare(bestConfig) != 0) {
              continue;
            }
          }
        }

        bestType = thisType;
//        bestOffset = thisOffset;
        bestConfig = thisConfig;
        bestPackage = typeSpec._package_;
        actualTypeIndex = (byte) realTypeIndex;

        // If no config was specified, any type will do, so skip
        if (config == NULL) {
          break;
        }
      }
    }

    if (bestType == NULL) {
      return BAD_INDEX;
    }

    if (bestEntry == null) {
      return BAD_INDEX;
    }

//    bestOffset += dtohl(bestType.entriesStart);
//
////    if (bestOffset > (dtohl(bestType.header.size)-sizeof(ResTable_entry))) {
//    int sizeOfResTableEntry = 2 // int size
//        + 2  // int flags
//        + 4; // struct ResStringPool_ref key: uint index
//    if (bestOffset > (dtohl(bestType.header.size)- sizeOfResTableEntry)) {
//      ALOGW("ResTable_entry at 0x%x is beyond type chunk data 0x%x",
//          bestOffset, dtohl(bestType.header.size));
//      return BAD_TYPE;
//    }
//    if ((bestOffset & 0x3) != 0) {
//      ALOGW("ResTable_entry at 0x%x is not on an integer boundary", bestOffset);
//      return BAD_TYPE;
//    }

//    final ResTable_entry* final entry = reinterpret_cast<final ResTable_entry*>(
//      reinterpret_cast<final uint8_t*>(bestType) + bestOffset);
//    final ResTableEntry entry = bestType.getEntry(bestOffset);
//    if (dtohs(entry.size) < sizeof(*entry)) {
    int ptrSize = 4;
//    if (dtohs(entry.size) < ptrSize) {
//    ALOGW("ResTable_entry size 0x%x is too small", dtohs(entry.size));
//    return BAD_TYPE;
//  }

    ResTableEntry entry = bestEntry;
    if (outEntryRef != null) {
      outEntryRef.set(
          entry.createEntry(bestType, bestPackage, specFlags, actualTypeIndex, bestConfig));
    }
    return NO_ERROR;
  }

  public void setParameters(ResTableConfig parameters) {
    this.parameters = parameters;
  }

  public int getTableCookie(int index) {
    return mHeaders.get(index).cookie;
  }

  private static final Map<String, Integer> sInternalNameToIdMap = new HashMap<>();
  static {
    sInternalNameToIdMap.put("^type", ResTableMap.ATTR_TYPE);
    sInternalNameToIdMap.put("^l10n", ResTableMap.ATTR_L10N);
    sInternalNameToIdMap.put("^min" , ResTableMap.ATTR_MIN);
    sInternalNameToIdMap.put("^max", ResTableMap.ATTR_MAX);
    sInternalNameToIdMap.put("^other", ResTableMap.ATTR_OTHER);
    sInternalNameToIdMap.put("^zero", ResTableMap.ATTR_ZERO);
    sInternalNameToIdMap.put("^one", ResTableMap.ATTR_ONE);
    sInternalNameToIdMap.put("^two", ResTableMap.ATTR_TWO);
    sInternalNameToIdMap.put("^few", ResTableMap.ATTR_FEW);
    sInternalNameToIdMap.put("^many", ResTableMap.ATTR_MANY);
  }

  public int identifierForName(String name, String type, String packageName) {
    return identifierForName(name, type, packageName, null);
  }

  public int identifierForName(String nameString, String type, String packageName,
      Ref<Integer> outTypeSpecFlags) {
//    if (kDebugTableSuperNoisy) {
//      printf("Identifier for name: error=%d\n", mError);
//    }
//    // Check for internal resource identifier as the very first thing, so
//    // that we will always find them even when there are no resources.
    if (nameString.startsWith("^")) {
      if (sInternalNameToIdMap.containsKey(nameString)) {
        if (outTypeSpecFlags != null) {
          outTypeSpecFlags.set(ResTableTypeSpec.SPEC_PUBLIC);
        }
        return sInternalNameToIdMap.get(nameString);
      }
      if (nameString.length() > 7)
        if (nameString.substring(1, 6).equals("index_")) {
          int index = Integer.getInteger(nameString.substring(7));
          if (Res_CHECKID(index)) {
            ALOGW("Array resource index: %d is too large.",
                index);
            return 0;
          }
          if (outTypeSpecFlags != null) {
            outTypeSpecFlags.set(ResTableTypeSpec.SPEC_PUBLIC);
          }
          return  Res_MAKEARRAY(index);
        }

      return 0;
    }

    if (mError != NO_ERROR) {
      return 0;
    }


    // Figure out the package and type we are looking in...
    // TODO(BC): The following code block was a best effort attempt to directly transliterate
    // C++ code which uses pointer artihmetic. Consider replacing with simpler logic

    boolean fakePublic = false;
    char[] name = nameString.toCharArray();
    int packageEnd = -1;
    int typeEnd = -1;
    int nameEnd = name.length;
    int pIndex = 0;
    while (pIndex < nameEnd) {
      char p = name[pIndex];
      if (p == ':') packageEnd = pIndex;
      else if (p == '/') typeEnd = pIndex;
      pIndex++;
    }
    int nameIndex = 0;
    if (name[nameIndex] == '@') {
      nameIndex++;
      if (name[nameIndex] == '*') {
        fakePublic = true;
        nameIndex++;
    }
  }
    if (nameIndex >= nameEnd) {
      return 0;
    }
    if (packageEnd != -1) {
        packageName = nameString.substring(nameIndex, packageEnd);
        nameIndex = packageEnd+1;
    } else if (packageName == null) {
      return 0;
    }
    if (typeEnd != -1) {
      type = nameString.substring(nameIndex, typeEnd);
      nameIndex = typeEnd+1;
    } else if (type == null) {
      return 0;
    }
    if (nameIndex >= nameEnd) {
      return 0;
    }
    nameString = nameString.substring(nameIndex, nameEnd);

//    nameLen = nameEnd-name;
//    if (kDebugTableNoisy) {
//      printf("Looking for identifier: type=%s, name=%s, package=%s\n",
//          String8(type, typeLen).string(),
//          String8(name, nameLen).string(),
//          String8(package, packageLen).string());
//    }
    final String attr = "attr";
    final String attrPrivate = "^attr-private";
    int NG = mPackageGroups.size();
    for (PackageGroup group : mPackageGroups.values()) {
      if (Strings.equals(packageName, group.name)) {
        if (kDebugTableNoisy) {
           System.out.println(String.format("Skipping package group: %s\n", group.name));
        }
        continue;
      }
      for (Package pkg : group.packages) {
        String targetType = type;

        do {
          int ti = pkg.typeStrings.indexOfString(targetType);
          if (ti < 0) {
            continue;
          }
          ti += pkg.typeIdOffset;
          int identifier = findEntry(group, ti, nameString, outTypeSpecFlags);
          if (identifier != 0) {
            if (fakePublic && outTypeSpecFlags != null) {
                        outTypeSpecFlags.set(outTypeSpecFlags.get() | ResTableTypeSpec.SPEC_PUBLIC);
            }
            return identifier;
          }
        } while (attr.compareTo(targetType) == 0
            && ((targetType = attrPrivate) != null)
            );
      }
      break;
    }
    return 0;
  }

  int findEntry(PackageGroup group, int typeIndex, String name, Ref<Integer> outTypeSpecFlags) {
    List<Type> typeList = group.types.get(typeIndex);
    for (Type type : typeList) {
      int ei = type._package_.keyStrings.indexOfString(name);
      if (ei < 0) {
        continue;
      }
      for (ResTableType resTableType : type.configs) {
        List<ResTableEntry> entries = resTableType.entries;
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++ ) {
          ResTableEntry entry = entries.get(entryIndex);
          if (entry == null) {
            continue;
          }
          if (dtohl(entry.key.index) == ei) {
            int resId = Res_MAKEID(group.id - 1, typeIndex, entryIndex);
            if (outTypeSpecFlags != null) {
              Ref<Entry> result = new Ref<>(null);
              if (getEntry(group, typeIndex, entryIndex, null, result) != NO_ERROR) {
                ALOGW("Failed to find spec flags for 0x%08x", resId);
                return 0;
              }
              outTypeSpecFlags.set(result.get().specFlags);
            }
            return resId;
          }
        }
      }
    }
    return 0;
  }

  public int getTableCount() {
    return mHeaders.size();
  }

  public ResStringPool getTableStringBlock(int index) {
    return mHeaders.get(index).values;
  }

  // A group of objects describing a particular resource package.
  // The first in 'package' is always the root object (from the resource
  // table that defined the package); the ones after are skins on top of it.
  // from ResourceTypes.cpp struct ResTable::PackageGroup
  public static class PackageGroup
  {
    public PackageGroup(
        ResTable _owner, final String _name, int _id,
        boolean appAsLib, boolean _isSystemAsset)
//        : owner(_owner)
//        , name(_name)
//        , id(_id)
//        , largestTypeId(0)
//        , dynamicRefTable(static_cast<uint8_t>(_id), appAsLib)
//        , isSystemAsset(_isSystemAsset)
    {
      this.owner = _owner;
      this.name = _name;
      this.id = _id;
      this.dynamicRefTable = new DynamicRefTable((byte) _id, appAsLib);
      this.isSystemAsset = _isSystemAsset;
    }

//    ~PackageGroup() {
//      clearBagCache();
//      final int numTypes = types.size();
//      for (int i = 0; i < numTypes; i++) {
//        final List<DataType> typeList = types.get(i);
//        final int numInnerTypes = typeList.size();
//        for (int j = 0; j < numInnerTypes; j++) {
//          if (typeList.get(j)._package_.owner == owner) {
//            delete typeList[j];
//          }
//        }
//      }
//
//      final int N = packages.size();
//      for (int i=0; i<N; i++) {
//        ResTablePackage pkg = packages[i];
//        if (pkg.owner == owner) {
//          delete pkg;
//        }
//      }
//    }

//    /**
//     * Clear all cache related data that depends on parameters/configuration.
//     * This includes the bag caches and filtered types.
//     */
//    void clearBagCache() {
//      for (int i = 0; i < typeCacheEntries.size(); i++) {
//        if (kDebugTableNoisy) {
//          printf("type=%zu\n", i);
//        }
//        final List<DataType> typeList = types.get(i);
//        if (!typeList.isEmpty()) {
//          TypeCacheEntry cacheEntry = typeCacheEntries.editItemAt(i);
//
//          // Reset the filtered configurations.
//          cacheEntry.filteredConfigs.clear();
//
//          bag_set[][] typeBags = cacheEntry.cachedBags;
//          if (kDebugTableNoisy) {
//            printf("typeBags=%p\n", typeBags);
//          }
//
//          if (isTruthy(typeBags)) {
//            final int N = typeList.get(0).entryCount;
//            if (kDebugTableNoisy) {
//              printf("type.entryCount=%zu\n", N);
//            }
//            for (int j = 0; j < N; j++) {
//              if (typeBags[j] && typeBags[j] != (bag_set *) 0xFFFFFFFF){
//                free(typeBags[j]);
//              }
//            }
//            free(typeBags);
//            cacheEntry.cachedBags = NULL;
//          }
//        }
//      }
//    }

    private void printf(String message, Object... arguments) {
      System.out.print(String.format(message, arguments));
    }

//    long findType16(final String type, int len) {
//      final int N = packages.size();
//      for (int i = 0; i < N; i++) {
//        sint index = packages[i].typeStrings.indexOfString(type, len);
//        if (index >= 0) {
//          return index + packages[i].typeIdOffset;
//        }
//      }
//      return -1;
//    }

    final ResTable owner;
    final String name;
    final int id;

    // This is mainly used to keep track of the loaded packages
    // and to clean them up properly. Accessing resources happens from
    // the 'types' array.
    List<Package> packages = new LinkedList<>();

    public final Map<Integer, List<Type>> types = new HashMap<>();

    byte largestTypeId;

    // Cached objects dependent on the parameters/configuration of this ResTable.
    // Gets cleared whenever the parameters/configuration changes.
    // These are stored here in a parallel structure because the data in `types` may
    // be shared by other ResTable's (framework resources are shared this way).
    ByteBucketArray<TypeCacheEntry> typeCacheEntries = new ByteBucketArray<TypeCacheEntry>() {
      @Override
      TypeCacheEntry newInstance() {
        return new TypeCacheEntry();
      }
    };

    // The table mapping dynamic references to resolved references for
    // this package group.
    // TODO: We may be able to support dynamic references in overlays
    // by having these tables in a per-package scope rather than
    // per-package-group.
    DynamicRefTable dynamicRefTable;

    // If the package group comes from a system asset. Used in
    // determining non-system locales.
    final boolean isSystemAsset;
  }

  // transliterated from https://android.googlesource.com/platform/frameworks/base/+/android-7.1.1_r13/libs/androidfw/ResourceTypes.cpp:3151

  // --------------------------------------------------------------------
// --------------------------------------------------------------------
// --------------------------------------------------------------------

//  struct ResTable::Header
  public static class Header
  {
//    Header(ResTable* _owner) : owner(_owner), ownedData(NULL), header(NULL),
//      resourceIDMap(NULL), resourceIDMapSize(0) { }

    public Header(ResTable owner) {
      this.owner = owner;
    }

//    ~Header()
//    {
//      free(resourceIDMap);
//    }

    ResTable            owner;
    Object                           ownedData;
    ResTableHeader          header;
    int                          size;
//    uint8_t*                  dataEnd;
    int                  dataEnd;
    int                          index;
    int                         cookie;

    ResStringPool                   values;
    int[]                       resourceIDMap;
    int                          resourceIDMapSize;
  };

  public static class Entry {
    ResTableConfig config;
    ResTableEntry entry;
    ResTableType type;
    int specFlags;
    Package _package_;

    StringPoolRef typeStr;
    StringPoolRef keyStr;
  }

  public static class MapEntry extends Entry {
    ResTable parent;
    // Number of name/value pairs that follow for FLAG_COMPLEX.
    int count;

    ResTableMap[] nameValuePairs;
  }

  // struct ResTable::DataType
  public static class Type {

    final Header header;
    final Package _package_;
    public final int entryCount;
    public ResTableTypeSpec typeSpec;
    public int[] typeSpecFlags;
    public IdmapEntries idmapEntries = new IdmapEntries();
    public List<ResTableType> configs;

    public Type(final Header _header, final Package _package, int count)
  //        : header(_header), package(_package), entryCount(count),
  //  typeSpec(NULL), typeSpecFlags(NULL) { }
    {
      this.header = _header;
      _package_ = _package;
      this.entryCount = count;
      this.typeSpec = null;
      this.typeSpecFlags = null;
      this.configs = new ArrayList<>();
    }
  }

//  struct ResTable::Package
  public static class Package {
//    Package(ResTable* _owner, final Header* _header, final ResTable_package* _package)
//        : owner(_owner), header(_header), package(_package), typeIdOffset(0) {
//    if (dtohs(package.header.headerSize) == sizeof(package)) {
//      // The package structure is the same size as the definition.
//      // This means it contains the typeIdOffset field.
//      typeIdOffset = package.typeIdOffset;
//    }

    public Package(ResTable owner, Header header, ResTablePackage _package) {
      this.owner = owner;
      this.header = header;
      this._package_ = _package;
    }

    final ResTable owner;
    final Header header;
    final ResTablePackage _package_;

    ResStringPool typeStrings;
    ResStringPool keyStrings;

    int typeIdOffset;
  };

  public static class bag_entry {
    public int stringBlock;
    public ResTableMap map = new ResTableMap(0, new ResValue(0, 0));
  }

  public void lock() {
    try {
      mLock.acquire();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void unlock() {
    mLock.release();
  }

  public int lockBag(int resID, Ref<bag_entry[]> outBag) {
    lock();

    int err = getBagLocked(resID, outBag, null);
    if (err < NO_ERROR) {
      //printf("*** get failed!  unlocking\n");
      mLock.release();
    }
    return err;
  }

  public int getBagLocked(int resID, Ref<bag_entry[]> outBag, Ref<Integer> outTypeSpecFlags) {
    if (mError != NO_ERROR) {
      return mError;
    }

    final int p = getResourcePackageIndex(resID);
    final int t = Res_GETTYPE(resID);
    final int e = Res_GETENTRY(resID);

    if (p < 0) {
      ALOGW("Invalid package identifier when getting bag for resource number 0x%08x", resID);
      return BAD_INDEX;
    }
    if (t < 0) {
      ALOGW("No type identifier when getting bag for resource number 0x%08x", resID);
      return BAD_INDEX;
    }

    //printf("Get bag: id=0x%08x, p=%d, t=%d\n", resID, p, t);
    PackageGroup grp = mPackageGroups.get(p);
    if (grp == NULL) {
      ALOGW("Bad identifier when getting bag for resource number 0x%08x", resID);
      return BAD_INDEX;
    }

    final List<Type> typeConfigs = grp.types.get(t);
    if (typeConfigs.isEmpty()) {
      ALOGW("Type identifier 0x%x does not exist.", t+1);
      return BAD_INDEX;
    }

    final int NENTRY = typeConfigs.get(0).entryCount;
    if (e >= (int)NENTRY) {
      ALOGW("Entry identifier 0x%x is larger than entry count 0x%x",
          e, (int)typeConfigs.get(0).entryCount);
      return BAD_INDEX;
    }

    // First see if we've already computed this bag...
    TypeCacheEntry cacheEntry = grp.typeCacheEntries.editItemAt(t);
    bag_set[] typeSet = cacheEntry.cachedBags;
//    if (isTruthy(typeSet)) {
//      bag_set set = typeSet[e];
//      if (isTruthy(set)) {
//        if (set != (bag_set) 0xFFFFFFFF){
//        if (set != SENTINEL_BAG_SET){
//          if (outTypeSpecFlags != NULL) {
//                    outTypeSpecFlags.set(set.typeSpecFlags);
//          }
//          outBag.set((bag_entry *) (set + 1);
//          if (kDebugTableSuperNoisy) {
//            ALOGI("Found existing bag for: 0x%x\n", resID);
//          }
//          return set.numAttrs;
//        }
//        ALOGW("Attempt to retrieve bag 0x%08x which is invalid or in a cycle.",
//            resID);
//        return BAD_INDEX;
//      }
//    }
//
    // Bag not found, we need to compute it!
    if (!isTruthy(typeSet)) {
      typeSet = new bag_set[NENTRY]; // (bag_set**)calloc(NENTRY, sizeof(bag_set*));
      //if (!typeSet) return NO_MEMORY;
      //cacheEntry.cachedBags = typeSet;
    }
//
//    // Mark that we are currently working on this one.
////    typeSet[e] = (bag_set*)0xFFFFFFFF;
//    typeSet[e] = SENTINEL_BAG_SET;

    if (kDebugTableNoisy) {
      ALOGI("Building bag: %x\n", resID);
    }

    // Now collect all bag attributes
    Ref<Entry> entryRef = new Ref<>(null);
    int err = getEntry(grp, t, e, mParams, entryRef);
    if (err != NO_ERROR) {
      return err;
    }
    Entry entry = entryRef.get();
    final short entrySize = dtohs(entry.entry.size);
    final int parent = entrySize >= 8 /*sizeof(ResTable_map_entry)*/
        ? dtohl(((ResTableMapEntry) entry.entry).parentIdent) : 0;
    final int count = entrySize >= 8 /*sizeof(ResTable_map_entry)*/
        ? dtohl(((ResTableMapEntry) entry.entry).count) : 0;

    int N = count;

    if (kDebugTableNoisy) {
      ALOGI("Found map: size=%x parent=%x count=%d\n", entrySize, parent, count);

      // If this map inherits from another, we need to start
      // with its parent's values.  Otherwise start out empty.
      ALOGI("Creating new bag, entrySize=0x%08x, parent=0x%08x\n", entrySize, parent);
    }

    // This is what we are building.
    Ref<bag_set> setRef = new Ref<>(null);

    if (isTruthy(parent)) {
      Ref<Integer> resolvedParent = new Ref<>(parent);

      // Bags encode a parent reference without using the standard
      // Res_value structure. That means we must always try to
      // resolve a parent reference in case it is actually a
      // TYPE_DYNAMIC_REFERENCE.
      err = grp.dynamicRefTable.lookupResourceId(resolvedParent);
      if (err != NO_ERROR) {
        ALOGE("Failed resolving bag parent id 0x%08x", parent);
        return UNKNOWN_ERROR;
      }

      final Ref<bag_entry[]> parentBag = new Ref<>(null);
      final Ref<Integer> parentTypeSpecFlags = new Ref<>(0);
      final int NP = getBagLocked(resolvedParent.get(), parentBag, parentTypeSpecFlags);
      final int NT = ((NP >= 0) ? NP : 0) + N;
//      set = (bag_set *) malloc(sizeof(bag_set) + sizeof(bag_entry) * NT);
      bag_set set = new bag_set(NT);
      setRef.set(set);
//      if (setRef.get() == NULL) {
//        return NO_MEMORY;
//      }
      if (NP > 0) {
//        memcpy(set + 1, parentBag, NP * sizeof(bag_entry));
        set.copyFrom(parentBag.get(), NP);
        set.numAttrs = NP;
        if (kDebugTableNoisy) {
          ALOGI("Initialized new bag with %zd inherited attributes.\n", NP);
        }
      } else {
        if (kDebugTableNoisy) {
          ALOGI("Initialized new bag with no inherited attributes.\n");
        }
        set.numAttrs = 0;
      }
      set.availAttrs = NT;
      set.typeSpecFlags = parentTypeSpecFlags.get();
    } else {
//      set = (bag_set *) malloc(sizeof(bag_set) + sizeof(bag_entry) * N);
      bag_set set = new bag_set(N);
      setRef.set(set);
//      if (set == NULL) {
//        return NO_MEMORY;
//      }
      set.numAttrs = 0;
      set.availAttrs = N;
      set.typeSpecFlags = 0;
    }

    bag_set set = setRef.get();
    set.typeSpecFlags |= entry.specFlags;

    // Now merge in the new attributes...
//    int curOff = (reinterpret_cast<uintptr_t>(entry.entry) - reinterpret_cast<uintptr_t>(entry.type))
//        + dtohs(entry.entry.size);
    ResTableMap map;
//    bag_entry* entries = (bag_entry*)(set+1);
    bag_entry[] entries = set.bag_entries;
    int curEntry = 0;
    int pos = 0;
    if (kDebugTableNoisy) {
      ALOGI("Starting with set %p, entries=%p, avail=%zu\n", set, entries, set.availAttrs);
    }
    while (pos < count) {
      if (kDebugTableNoisy) {
//        ALOGI("Now at %p\n", curOff);
        ALOGI("Now at %p\n", curEntry);
      }

//      if (curOff > (dtohl(entry.type.header.size)-sizeof(ResTable_map))) {
//        ALOGW("ResTable_map at %d is beyond type chunk data %d",
//            (int)curOff, dtohl(entry.type.header.size));
//        return BAD_TYPE;
//      }
//      map = (final ResTable_map*)(((final uint8_t*)entry.type) + curOff);
      map = ((MapEntry) entry).nameValuePairs[curEntry];
      N++;

      Ref<Integer> newName = new Ref<>(htodl(map.nameIdent));
      if (!Res_INTERNALID(newName.get())) {
        // Attributes don't have a resource id as the name. They specify
        // other data, which would be wrong to change via a lookup.
        if (grp.dynamicRefTable.lookupResourceId(newName) != NO_ERROR) {
          ALOGE("Failed resolving ResTable_map name at %d with ident 0x%08x",
              (int) curEntry, (int) newName.get());
          return UNKNOWN_ERROR;
        }
      }

      boolean isInside;
      int oldName = 0;
      while ((isInside=(curEntry < set.numAttrs))
          && (oldName=entries[curEntry].map.nameIdent) < newName.get()) {
        if (kDebugTableNoisy) {
          ALOGI("#%zu: Keeping existing attribute: 0x%08x\n",
              curEntry, entries[curEntry].map.nameIdent);
        }
        curEntry++;
      }

      if ((!isInside) || oldName != newName.get()) {
        // This is a new attribute...  figure out what to do with it.
        if (set.numAttrs >= set.availAttrs) {
          // Need to alloc more memory...
                final int newAvail = set.availAttrs+N;
//          set = (bag_set[])realloc(set,
//              sizeof(bag_set)
//                  + sizeof(bag_entry)*newAvail);
          set.resizeBagEntries(newAvail);
          if (set == NULL) {
            return NO_MEMORY;
          }
          set.availAttrs = newAvail;
//          entries = (bag_entry*)(set+1);
          entries = set.bag_entries;
          if (kDebugTableNoisy) {
            ALOGI("Reallocated set %p, entries=%p, avail=%zu\n",
                set, entries, set.availAttrs);
          }
        }
        if (isInside) {
          // Going in the middle, need to make space.
//          memmove(entries+curEntry+1, entries+curEntry,
//              sizeof(bag_entry)*(set.numAttrs-curEntry));
          System.arraycopy(entries, curEntry, entries, curEntry + 1, set.numAttrs - curEntry);
          set.numAttrs++;
        }
        if (kDebugTableNoisy) {
          ALOGI("#%zu: Inserting new attribute: 0x%08x\n", curEntry, newName);
        }
      } else {
        if (kDebugTableNoisy) {
          ALOGI("#%zu: Replacing existing attribute: 0x%08x\n", curEntry, oldName);
        }
      }

      bag_entry cur = entries[curEntry];
      if (cur == null) {
        cur = entries[curEntry] = new bag_entry();
      }

      cur.stringBlock = entry._package_.header.index;
      cur.map.nameIdent = newName.get();
//      cur.map.value.copyFrom_dtoh(map.value);
      cur.map.value = new ResValue(map.value);
      err = grp.dynamicRefTable.lookupResourceValue(cur.map.value);
      if (err != NO_ERROR) {
        ALOGE("Reference item(0x%08x) in bag could not be resolved.", cur.map.value.data);
        return UNKNOWN_ERROR;
      }

      if (kDebugTableNoisy) {
        ALOGI("Setting entry #%zu %p: block=%zd, name=0x%08d, type=%d, data=0x%08x\n",
            curEntry, cur, cur.stringBlock, cur.map.nameIdent,
            cur.map.value.dataType, cur.map.value.data);
      }

      // On to the next!
      curEntry++;
      pos++;
//        final int size = dtohs(map.value.size);
//      curOff += size + sizeof(*map)-sizeof(map.value);
    };

    if (curEntry > set.numAttrs) {
      set.numAttrs = curEntry;
    }

    // And this is it...
    typeSet[e] = set;
    if (isTruthy(set)) {
      if (outTypeSpecFlags != NULL) {
        outTypeSpecFlags.set(set.typeSpecFlags);
      }
      outBag.set(set.bag_entries);
      if (kDebugTableNoisy) {
        ALOGI("Returning %zu attrs\n", set.numAttrs);
      }
      return set.numAttrs;
    }
    return BAD_INDEX;
  }

  public void unlockBag(Ref<bag_entry[]> bag) {
    unlock();
  }

  static class bag_set {
    int numAttrs;    // number in array
    int availAttrs;  // total space in array
    int typeSpecFlags;
    // Followed by 'numAttr' bag_entry structures.

    bag_entry[] bag_entries;

    public bag_set(int entryCount) {
      bag_entries = new bag_entry[entryCount];
    }

    public void copyFrom(bag_entry[] parentBag, int count) {
      for (int i = 0; i < count; i++) {
        bag_entries[i] = parentBag[i];
      }
    }

    public void resizeBagEntries(int newEntryCount) {
      bag_entry[] newEntries = new bag_entry[newEntryCount];
      System.arraycopy(bag_entries, 0, newEntries, 0, Math.min(bag_entries.length, newEntryCount));
      bag_entries = newEntries;
    }
  };

  /**
   * Configuration dependent cached data. This must be cleared when the configuration is
   * changed (setParameters).
   */
  static class TypeCacheEntry {
//    TypeCacheEntry() : cachedBags(NULL) {}

    // Computed attribute bags for this type.
//    bag_set** cachedBags;
    bag_set[] cachedBags;

    // Pre-filtered list of configurations (per asset path) that match the parameters set on this
    // ResTable.
    List<List<ResTableType>> filteredConfigs;
  };


  private int Res_MAKEID(int packageId, int typeId, int entryId) {
    return (((packageId+1)<<24) | (((typeId+1)&0xFF)<<16) | (entryId&0xFFFF));
  }

}