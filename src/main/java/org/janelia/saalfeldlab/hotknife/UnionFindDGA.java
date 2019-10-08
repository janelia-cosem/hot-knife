package org.janelia.saalfeldlab.hotknife;

import java.util.*;

import net.imglib2.algorithm.util.unionfind.IntArrayRankedUnionFind;

public class UnionFindDGA {
	public Map<Long, Long> globalIDtoRootID;
	public Map<Long, Integer> globalIDtoRank;

	public UnionFindDGA(long[][] initialGlobalIDtoGlobalID) {
		this.globalIDtoRootID = new HashMap<Long, Long>();
		this.globalIDtoRank = new HashMap<Long, Integer>();
		for (int i = 0; i < initialGlobalIDtoGlobalID.length; i++) {
			long globalID1 = initialGlobalIDtoGlobalID[i][0];
			long globalID2 = initialGlobalIDtoGlobalID[i][1];
			/*// want to make sure first global ID is already in it
			if (globalIDtoRootID.containsKey(globalID2) && !globalIDtoRootID.containsKey(globalID1)) {
				initialGlobalIDtoGlobalID[i][0] = globalID2;
				initialGlobalIDtoGlobalID[i][1] = globalID1;
			}*/
			globalIDtoRootID.put(globalID1, globalID1);
			globalIDtoRootID.put(globalID2, globalID2);
			globalIDtoRank.put(globalID1, 0);
			globalIDtoRank.put(globalID2, 0);
		}

		for (int i = 0; i < initialGlobalIDtoGlobalID.length; i++) {
			long globalID1 = initialGlobalIDtoGlobalID[i][0];
			long globalID2 = initialGlobalIDtoGlobalID[i][1];
			union(globalID1, globalID2);
		}
		
	}

	public long findRoot(long globalID) {
		if (globalIDtoRootID.get(globalID) != globalID) {
			globalIDtoRootID.put(globalID, findRoot(globalIDtoRootID.get(globalID)));
		}
		return globalIDtoRootID.get(globalID);

	}
	
	public void renumberRoots() {
		Map <Long, Long> renumberRootsMap = new HashMap<Long, Long>();
		long count = 1;
		for (Map.Entry<Long, Long> entry : globalIDtoRootID.entrySet()) {
			long key = entry.getKey();
			long root = findRoot(key);
			
			if (!renumberRootsMap.containsKey(root)) {
				renumberRootsMap.put(root, count);
				count++;
			}
		}
		
		for (Map.Entry<Long, Long> entry : globalIDtoRootID.entrySet()) {
			long key = entry.getKey();
			long value = entry.getValue();			
			globalIDtoRootID.put(key, renumberRootsMap.get(value));
		}
	}

	public void union(long globalID1, long globalID2) {
		long globalID1Root = findRoot(globalID1);
		long globalID2Root = findRoot(globalID2);

		// globalID1 and globalID2 are already in the same set
		if (globalID1Root == globalID2Root)
			return;

		// globalID1 and globalID2 are not currently in the same set, so merge them
		if (globalIDtoRank.get(globalID1) < globalIDtoRank.get(globalID2)) {
			// swap roots
			long tmp = globalID1Root;
			globalID1Root = globalID2Root;
			globalID2Root = tmp;
		}
		// merge globalID2Root into globalID1Root
		globalIDtoRootID.put(globalID2Root, globalID1Root);
		
		if (globalIDtoRank.get(globalID1Root) == globalIDtoRank.get(globalID2Root))
			globalIDtoRank.put(globalID1Root, globalIDtoRank.get(globalID1Root) + 1);
	}

	public static void main(final String[] args) {
		long[][] initialGlobalIDtoGlobalID = { {1,1}, { 1, 3 }, {3,3}, { 3, 2 },{2,20}, {14,14}, { 7, 6 }, { 5, 6 }, { 10, 5 }, {3,14}, { 9, 7 }, { 4, 7 },
				{ 7, 8 }, { 8, 10 }, { 8, 12 }, { 8, 15 }, { 16, 17 },{14,1}};
		//long [][] initialGlobalIDtoGlobalID = {{1,2},{2,3},{3,4},{5,3},{6,7},{8,6},{6,7},{6,1}};
		/*long[][] initialGlobalIDtoGlobalID = new long [1000][2];
		for(int i=0; i< 1000;i++) {
			initialGlobalIDtoGlobalID[i][0]=(long)(Math.random()*200);
			initialGlobalIDtoGlobalID[i][1]=(long)(Math.random()*200);
		}*/
		long startTime = System.currentTimeMillis();
		UnionFindDGA testing = new UnionFindDGA(initialGlobalIDtoGlobalID);
		for (Map.Entry<Long, Long> entry : testing.globalIDtoRootID.entrySet()) {
			System.out.println("final " + entry.getKey() + ":" + entry.getValue().toString());
		}

		testing.renumberRoots();
		for (Map.Entry<Long, Long> entry : testing.globalIDtoRootID.entrySet()) {
			System.out.println("renumbered " + entry.getKey() + ":" + entry.getValue());
		}
		System.out.println(System.currentTimeMillis() - startTime);

		startTime = System.currentTimeMillis();
		IntArrayRankedUnionFind arrayRankedUnionFind = new IntArrayRankedUnionFind(18);
		for (int i = 0; i < initialGlobalIDtoGlobalID.length; i++) {
			long globalID1 = initialGlobalIDtoGlobalID[i][0];
			long globalID2 = initialGlobalIDtoGlobalID[i][1];
			arrayRankedUnionFind.join(arrayRankedUnionFind.findRoot(globalID1),
					arrayRankedUnionFind.findRoot(globalID2));
		}
		int [] temp = new int [18];
		for (int i = 0; i < 18; i++) {
			temp[i]=arrayRankedUnionFind.findRoot(i);
			System.out.println("theirs " + i + " " + temp[i]);
		}
		System.out.println(System.currentTimeMillis() - startTime);
	/*	long[][] endresult = {{340317256,191217671},
				{127182743,191217671},
				{172192325,447234275},
				{341191772,442267447},
				{767296951,959239839},
				{50191085,442267447},
				{319191831,442267447},
				{639362575,442267447},
				{341191752,319191895},
				{127377335,442267447},
				{831218091,831213225},
				{383358717,447234275},
				{191359810,442267447},
				{127203257,447234275},
				{999416767,383359635},
				{746005575,746005959},
				{999414691,342191998},
				{926256902,959239839},
				{258191190,262191186},
				{201192395,255191447},
				{959404313,442267447},
				{383358703,442267447},
				{319359806,442267447},
				{255371009,191217671},
				{258191192,262191252},
				{384327336,383329353},
				{348333191,609313370},
				{217187511,442267447},
				{895399191,442267447},
				{886383768,575239487},
				{8384562,447234275},
				{255369079,442267447},
				{581237492,575239487},
				{319191895,442267447},
				{516226704,442267447},
				{588231515,255365245},
				{319195999,575239487},
				{215223439,215223439},
				{348335256,831213225},
				{252188502,267190427},
				{834384346,999414691},
				{831388136,442267447},
				{255190867,442267447},
				{895399220,831213225},
				{299325885,295319896},
				{191326986,191319999},
				{255358805,442267447},
				{746005511,746005447},
				{639374959,127373483},
				{703383967,454237192},
				{447359698,831213225},
				{777256491,767259505},
				{839256703,442267447},
				{224220452,215223439},
				{703383959,447234275},
				{127319831,127319831},
				{831383820,442267447},
				{536326000,454237192},
				{978191044,999196019},
				{575357632,63191201},
				{555229588,551230575},
				{319206283,442267447},
				{127319833,127319831},
				{319191959,442267447},
				{355319230,191217671},
				{447207987,350191940},
				{767313193,442267447},
				{728384511,442267447},
				{959383991,651384999},
				{639231617,639231617},
				{895237575,442267447},
				{179192373,350191940},
				{319366038,447234275},
				{767372575,63191201},
				{619285579,619285575},
				{386209703,442267447},
				{511348447,831213225},
				{127319856,127319831},
				{640304770,639319768},
				{127379262,831213225},
				{216192072,442267447},
				{628231640,639231617},
				{775255710,442267447},
				{255190937,831213225},
				{575353575,442267447},
				{619285575,619285575},
				{255192987,447234275},
				{746005703,746005959},
				{767378703,442267447},
				{511346383,191217671},
				{319191999,442267447},
				{211182639,631320383},
				{439263895,191217671},
				{63361916,442267447},
				{447359505,442267447},
				{895233511,831213225},
				{767255815,454237192},
				{255199115,447234275},
				{575357684,442267447},
				{191372201,831213225},
				{295319896,295319896},
				{767360383,127373483},
				{639319768,831213225},
				{600320768,447234275},
				{191318959,191319999},
				{511352497,831213225},
				{639319767,831213225},
				{237321960,239319959},
				{703383843,191217671},
				{63204117,631320383},
				{255367145,191217671},
				{370253507,368252512},
				{167209249,447234275},
				{127360847,63191201},
				{895245703,731256832},
				{767305063,447234275},
				{999426895,575239487},
				{767380832,454237192},
				{767364444,63191201},
				{698306941,693299961},
				{511231645,63191201},
				{999412603,342191998},
				{255197147,63191201},
				{703389954,383359635},
				{558250408,558250408},
				{746005639,746005575},
				{511231625,442267447},
				{831390079,442267447},
				{96384332,442267447},
				{319204339,442267447},
				{767352132,442267447},
				{320191469,319195472},
				{191183775,442267447},
				{895241639,766255832},
				{127182703,63191201},
				{77192295,447234275},
				{959229999,959239839},
				{831250575,442267447},
				{191363680,442267447},
				{63365838,895399191},
				{435235064,511300063},
				{43191192,350191940},
				{239322956,239319959},
				{319191575,350191940},
				{127325831,127319831},
				{141384063,383223165},
				{767255727,442267447},
				{831383703,442267447},
				{959404047,63191201},
				{126191268,255365245},
				{127211191,442267447},
				{499223779,485220767},
				{819235319,831213225},
				{831246511,63191201},
				{767255711,442267447},
				{8247641,8247641},
				{587321895,575239487},
				{147192301,350191940},
				{270638,270638},
				{147192303,319206283},
				{999221925,959239839},
				{291192951,255190937},
				{767255703,442267447},
				{962383984,651384999},
				{607321375,444319633},
				{383361015,631320383},
				{141384002,63357993},
				{746005831,746005959},
				{319195703,442267447},
				{703383775,442267447},
				{511338319,447234275},
				{447363999,831213225},
				{191187539,63191201},
				{383358959,442267447},
				{147192317,79192291},
				{767255682,442267447},
				{18243631,8247641},
				{703383767,191217671},
				{767319175,442267447},
				{319191609,442267447},
				{127211220,575239487},
				{794383896,831213225},
				{767376639,442267447},
				{319214149,442267447},
				{596255439,596255439},
				{511354684,63191201},
				{996225577,895237575},
				{235192141,262191252},
				{767354103,442267447},
				{575349511,442267447},
				{319191639,442267447},
				{767382767,442267447},
				{295325896,295319896},
				{831383767,191217671},
				{799256259,831213225},
				{959404109,63191201},
				{972451999,442267447},
				{319361635,442267447},
				{337315191,442267447},
				{511254295,511254295},
				{746005767,746005703},
				{575355705,63191201},
				{800384704,447234275},
				{427229185,383223165},
				{321237597,316256599},
				{484255140,831213225},
				{703383703,191217671},
				{352208256,831213225},
				{127383063,454237192},
				{895255773,191217671},
				{831383564,442267447},
				{786384734,442267447},
				{715385639,442267447},
				{319361664,454237192},
				{895255767,831213225},
				{426234092,442267447},
				{127331846,127319831},
				{319191703,442267447},
				{767398959,442267447},
				{255188641,442267447},
				{833384504,442267447},
				{319195795,267190427},
				{255360685,255365245},
				{746005999,746005959},
				{63183453,63357993},
				{999191063,959202127},
				{261244427,255247427},
				{511223779,511223779},
				{774395831,959487999},
				{450214773,442267447},
				{767263769,447234275},
				{191361732,442267447},
				{191376078,444319633},
				{831395879,191217671},
				{760255738,767255404},
				{169192383,831213225},
				{767263767,442267447},
				{767255575,831213225},
				{831397922,191217671},
				{293230807,280229767},
				{746005959,746005959},
				{81192293,255365245},
				{511354831,831213225},
				{586277703,631320383},
				{135313831,127319831},
				{63390335,185192703},
				{760255720,766255832},
				{703338581,831213225},
				{848255798,444319633},
				{450214767,442267447},
				{703383639,191217671},
				{447359767,447234275},
				{108384202,609313370},
				{127383127,447234275},
				{255192821,442267447},
				{952256768,959239839},
				{511360959,191217671},
				{255192819,442267447},
				{321192687,319195703},
				{191185577,442267447},
				{511362999,454237192},
				{191191723,191217671},
				{319191767,442267447},
				{615320959,81384387},
				{265192989,447234275},
				{319197909,831213225},
				{319337180,63191201},
				{209192365,454237192},
				{511358892,442267447},
				{831391831,191217671},
				{999191127,442267447},
				{831383639,442267447},
				{999410250,191217671},
				{831397973,442267447},
				{132320831,127319831},
				{447363959,191217671},
				{831264850,895237575},
				{63392280,444319633},
				{999414384,442267447},
				{831262831,831213225},
				{108384145,191217671},
				{255192785,442267447},
				{767255633,831213225},
				{703383559,442267447},
				{999191139,442267447},
				{511250327,511254295},
				{746005895,746005831},
				{319195895,442267447},
				{191191703,444319633},
				{447361881,255365245},
				{703332383,831213225},
				{139203001,255365245},
				{129384001,63357993},
				{447359827,319361635},
				{831387767,191217671},
				{511354756,63191201},
				{151192321,267190427},
				{185192717,442267447},
				{349191895,447234275},
				{193192331,267190427},
				{171192381,180192292},
				{193192329,267190427},
				{171192379,180192292},
				{63371734,831213225},
				{787256575,442267447},
				{385328353,383329353},
				{587231511,267190427},
				{703269879,693299961},
				{38191192,831213225},
				{667319769,442267447},
				{887383749,442267447},
				{195309959,191319999},
				{383360255,442267447},
				{231192559,267190427},
				{705353763,703383767},
				{231192557,231192559},
				{213192347,267190427},
				{447215767,442267447},
				{342191700,267190427},
				{673319443,63191201},
				{191191383,442267447},
				{703388639,191217671},
				{831384505,442267447},
				{447215775,442267447},
				{999211447,831213225},
				{999225781,959239839},
				{255364357,442267447},
				{127385001,442267447},
				{319363383,442267447},
				{127215063,442267447},
				{560321319,442267447},
				{839383117,383223165},
				{447234275,191217671},
				{999205315,767255404},
				{853383309,447234275},
				{191187255,831213225},
				{959208319,442267447},
				{895280905,959239839},
				{319195472,319195472},
				{511319084,831213225},
				{127368640,442267447},
				{810383895,191217671},
				{523230579,442267447},
				{457203997,350191940},
				{959202127,442267447},
				{703386511,442267447},
				{501231491,255365245},
				{598290895,575239487},
				{867383771,831213225},
				{999201255,442267447},
				{319195500,442267447},
				{258664,255667},
				{200192384,447234275},
				{774255703,442267447},
				{191367450,575239487},
				{959206239,454237192},
				{116192408,350191940},
				{63170999,831213225},
				{383360175,575239487},
				{171192387,180192292},
				{959234901,959239839},
				{319363444,63191201},
				{191367446,191217671},
				{347205326,345203319},
				{481255120,444319633},
				{383360089,63357993},
				{447238191,454237192},
				{936256832,959239839},
				{264191280,447234275},
				{63172947,442267447},
				{383329353,383329353},
				{767260459,447234275},
				{703341439,63191201},
				{527232511,350191940},
				{677384948,703383767},
				{319218065,442267447},
				{677384951,635299921},
				{895401926,447234275},
				{895397831,191217671},
				{959447981,191217671},
				{358205380,358203385},
				{279193,279193},
				{631320383,191217671},
				{891256767,767255404},
				{358203385,358203385},
				{319195559,350191940},
				{959210383,442267447},
				{695319511,63357993},
				{783256583,442267447},
				{319191471,319195472},
				{391208440,383210437},
				{695319518,695319511},
				{447359001,454237192},
				{319363516,575239487},
				{191365584,442267447},
				{767405831,63191201},
				{703343447,634296869},
				{319363511,575239487},
				{383312999,383312999},
				{285192767,319191895},
				{348200448,348200448},
				{895393692,383223165},
				{125384123,127383127},
				{895399837,191217671},
				{740386534,575239487},
				{154319866,127319831},
				{703404833,442267447},
				{319191499,63191201},
				{145384037,141384002},
				{208213670,191217671},
				{262190448,267190427},
				{63191319,442267447},
				{681306696,191217671},
				{63172887,442267447},
				{511319215,255226001},
				{191193,191217671},
				{63191322,442267447},
				{735254832,731256832},
				{846383255,63191201},
				{767367000,831213225},
				{255362526,255365245},
				{715384901,447234275},
				{999217511,831213225},
				{767293265,454237192},
				{319193579,575239487},
				{673319631,831213225},
				{447238215,191217671},
				{191191447,575239487},
				{356202440,348200448},
				{191191453,63191201},
				{575342770,831213225},
				{104191180,442267447},
				{822256639,831213225},
				{63396037,442267447},
				{511214973,442267447},
				{895213143,831213225},
				{447353255,447234275},
				{63191255,79192291},
				{191369340,442267447},
				{127177857,255365245},
				{330203323,267190427},
				{383221199,191217671},
				{342191998,442267447},
				{319358999,442267447},
				{639321403,442267447},
				{191191111,442267447},
				{959404553,959403503},
				{676332408,454237192},
				{806249255,442267447},
				{63377645,831213225},
				{352253903,349257903},
				{831384255,255365245},
				{191354964,81384387},
				{905383619,831386671},
				{276319043,276319043},
				{175192397,180192292},
				{786255331,784257334},
				{255319049,269319089},
				{350191584,831213225},
				{63379590,442267447},
				{831384268,575239487},
				{511216953,191217671},
				{747255758,575239487},
				{566301319,609313370},
				{297251569,294256567},
				{870383785,63357993},
				{905383611,831386671},
				{895219221,831213225},
				{447255015,63191201},
				{767260393,447234275},
				{63191191,442267447},
				{165192327,319206283},
				{445268458,442267447},
				{895211065,809260127},
				{191217671,191217671},
				{63191201,191217671},
				{191371277,831213225},
				{383360447,454237192},
				{823384699,831386671},
				{640316762,587321895},
				{127372517,191217671},
				{319365245,831213225},
				{478255092,511319084},
				{255247427,255247427},
				{831384304,831213225},
				{365235896,63191201},
				{287337055,269319089},
				{575234511,319195703},
				{639319441,442267447},
				{63197257,454237192},
				{316256599,316256599},
				{575234521,442267447},
				{767350319,575239487},
				{639370639,442267447},
				{368252512,368252512},
				{709265875,693299961},
				{731256832,442267447},
				{75192295,79192291},
				{757255871,63191201},
				{447213828,63191201},
				{255196307,442267447},
				{383360383,63191201},
				{127355967,444319633},
				{383223165,191217671},
				{693299961,693299961},
				{767346194,442267447},
				{447240455,442267447},
				{456237189,454237192},
				{959230617,442267447},
				{274191328,63357993},
				{639384999,63191201},
				{269319089,269319089},
				{63381517,442267447},
				{447353191,63191201},
				{807257043,831213225},
				{383360279,63191201},
				{959224575,442267447},
				{770256447,767259505},
				{895284875,959239839},
				{63174681,63357993},
				{767288928,959239839},
				{895221383,447234275},
				{499269151,484255140},
				{895280831,959239839},
				{63176743,63191201},
				{285192987,442267447},
				{767415896,703383767},
				{298191392,442267447},
				{831386219,575239487},
				{191189129,831213225},
				{953256765,959239839},
				{511214997,511214973},
				{536319001,454237192},
				{185192703,442267447},
				{191226013,442267447},
				{639313383,447234275},
				{811383900,191217671},
				{831229327,999201255},
				{301192697,267190427},
				{203192343,255191319},
				{831229319,999201255},
				{959403324,442267447},
				{831231391,444319633},
				{959251769,959239839},
				{762255738,767255703},
				{250192638,442267447},
				{959251767,959239839},
				{529255319,511254295},
				{197291999,191319999},
				{255191824,442267447},
				{999212971,959239839},
				{863383376,634296869},
				{999419830,442267447},
				{831217063,442267447},
				{314191511,350191940},
				{325191511,331191490},
				{257203055,255226001},
				{155327869,127319831},
				{517214955,191217671},
				{127357908,442267447},
				{63176581,341191772},
				{99191201,442267447},
				{999212999,959239839},
				{383359635,442267447},
				{831229383,831213225},
				{999239619,959239839},
				{483248256,63191201},
				{102384202,255365245},
				{63383447,442267447},
				{999239645,959239839},
				{959247739,959239839},
				{79192291,442267447},
				{959247735,959239839},
				{159192353,831213225},
				{813384730,575239487},
				{63383461,442267447},
				{809260127,442267447},
				{265237417,255247427},
				{976191048,999196019},
				{899254769,191217671},
				{127179773,444319633},
				{831213024,442267447},
				{768256423,767255404},
				{870383502,442267447},
				{63203243,191217671},
				{767420882,81384387},
				{98384302,444319633},
				{260191224,442267447},
				{349257903,349257903},
				{831255821,191217671},
				{859256767,766255832},
				{255191993,831213225},
				{772256394,444319633},
				{888383587,442267447},
				{63383383,185192703},
				{447360571,442267447},
				{383205957,255365245},
				{199192397,267190427},
				{540253351,511250327},
				{191356912,831213225},
				{959403447,442267447},
				{767369511,442267447},
				{511353575,609313370},
				{999433999,442267447},
				{833256715,766255832},
				{319358890,831213225},
				{703319375,831213225},
				{512223779,511223779},
				{511355602,255359716},
				{444327635,831213225},
				{185192355,180192292},
				{255357843,442267447},
				{767383830,442267447},
				{870383437,442267447},
				{999411491,63357993},
				{511333065,63191201},
				{767305999,959239839},
				{551230575,551230575},
				{242191063,831213225},
				{383205993,631320383},
				{959403503,191217671},
				{63387397,442267447},
				{639316701,63357993},
				{761297322,447234275},
				{831237447,731256832},
				{447360615,442267447},
				{383361547,442267447},
				{277290064,277290064},
				{191319999,239319959},
				{417213447,63191201},
				{466218770,466218770},
				{767383905,831213225},
				{63178535,350191940},
				{767383895,831213225},
				{191358861,255356959},
				{813270163,831213225},
				{191373188,444319633},
				{895398323,447234275},
				{767308114,442267447},
				{895398319,442267447},
				{999411557,63357993},
				{319365107,63357993},
				{736255774,767255703},
				{63203131,831213225},
				{895402079,255365245},
				{999409298,63191201},
				{127382162,447234275},
				{319192589,442267447},
				{831253639,442267447},
				{895402063,447234275},
				{383210437,383210437},
				{707368767,999419830},
				{383235009,511300063},
				{831310999,959239839},
				{383357893,191217671},
				{831255703,442267447},
				{383235017,442267447},
				{462214828,191217671},
				{885383661,442267447},
				{831257767,442267447},
				{639363895,191217671},
				{383359989,442267447},
				{511335255,442267447},
				{127363770,831213225},
				{255189531,267190427},
				{255341079,269319089},
				{769282287,767293265},
				{703392985,383359635},
				{3384581,442267447},
				{383210478,442267447},
				{980191056,999196019},
				{319196731,442267447},
				{531235477,511234475},
				{598259433,596255439},
				{243192630,267190427},
				{155192329,350191940},
				{319344194,442267447},
				{511228704,63191201},
				{255361633,442267447},
				{555217995,442267447},
				{831255767,191217671},
				{948231641,731256832},
				{383357831,191217671},
				{283192767,350191940},
				{255189610,442267447},
				{255337049,269319089},
				{831393003,442267447},
				{300319886,295319896},
				{57384450,831213225},
				{442267447,191217671},
				{703384704,191217671},
				{534229689,516226704},
				{127380217,831213225},
				{191213067,442267447},
				{309192477,267190427},
				{842383172,831213225},
				{807249263,447234275},
				{831304959,959239839},
				{485220767,485220767},
				{999433959,575239487},
				{127183593,442267447},
				{521321068,831213225},
				{815383832,442267447},
				{814241037,442267447},
				{447221550,341191772},
				{63368781,831213225},
				{239317960,191319999},
				{771256435,999211447},
				{635299921,442267447},
				{834235447,731256832},
				{127367704,255365245},
				{195192369,447234275},
				{509322191,442267447},
				{301196389,447234275},
				{677301705,63357993},
				{959403191,442267447},
				{895256773,444319633},
				{857267842,447234275},
				{350191940,191217671},
				{63207001,81384387},
				{767260697,442267447},
				{286332083,269319089},
				{831386671,442267447},
				{195192331,442267447},
				{767401999,442267447},
				{885383537,191217671},
				{354191528,442267447},
				{127384097,191217671},
				{319211197,447234275},
				{253201064,831213225},
				{195192423,255195383},
				{195192421,255195383},
				{350191896,442267447},
				{195192425,255191447},
				{758255870,767255404},
				{127187547,342191998},
				{710383512,442267447},
				{277321045,276319043},
				{239319959,239319959},
				{575340447,511335255},
				{319338205,447353191},
				{959403255,191217671},
				{255359716,442267447},
				{255191767,267190427},
				{447223629,341191772},
				{319360736,442267447},
				{643228607,639231617},
				{999222887,959239839},
				{467213875,447234275},
				{444319633,191217671},
				{831257703,442267447},
				{749386053,831213225},
				{533320255,319364185},
				{138192327,442267447},
				{191377047,191217671},
				{391309997,383312999},
				{191192421,255195383},
				{522316064,831213225},
				{191192423,79192291},
				{127369620,442267447},
				{717384902,831213225},
				{914243704,444319633},
				{767255475,63191201},
				{266191319,442267447},
				{519256313,511254295},
				{609313370,442267447},
				{767259575,831213225},
				{438266896,191217671},
				{63393236,255365245},
				{575355999,444319633},
				{63186387,442267447},
				{767253411,767255404},
				{286191383,831213225},
				{383211725,442267447},
				{249192641,447215767},
				{999409036,447234275},
				{530258309,511254295},
				{767253413,767255404},
				{344191644,442267447},
				{847256735,831213225},
				{255191319,442267447},
				{442219641,341191772},
				{999421364,442267447},
				{770255813,454237192},
				{767255447,731256832},
				{767273879,895237575},
				{746005063,746005191},
				{999208383,767255404},
				{959201053,442267447},
				{675384949,635299921},
				{828383063,442267447},
				{715383961,454237192},
				{703383511,81384387},
				{255195403,267190427},
				{294256567,294256567},
				{383207575,442267447},
				{68383319,255365245},
				{193189319,255191319},
				{283192999,63191201},
				{383209631,831213225},
				{255363440,447234275},
				{831385540,191217671},
				{645304383,267190427},
				{697384920,255365245},
				{191190319,442267447},
				{767259633,831213225},
				{959213415,999211447},
				{959217511,831213225},
				{811247127,442267447},
				{447354084,383223165},
				{895394575,442267447},
				{999196127,999196019},
				{399221145,383223165},
				{703342527,442267447},
				{767269871,63191201},
				{575249437,596255439},
				{831383511,442267447},
				{607281769,575239487},
				{127189455,63357993},
				{959203191,442267447},
				{127216074,442267447},
				{154212177,442267447},
				{999198191,442267447},
				{255191383,442267447},
				{999411187,575239487},
				{383211700,442267447},
				{63393187,444319633},
				{895394618,442267447},
				{463255035,342191998},
				{767255511,731256832},
				{332320191,63191201},
				{651384999,444319633},
				{999409126,447234275},
				{280188,279193},
				{283192989,447234275},
				{190321959,191319999},
				{77384419,63191201},
				{127176981,831213225},
				{639385758,695319511},
				{127191319,447234275},
				{895402971,447234275},
				{639309982,693299961},
				{350319209,447353191},
				{211192347,255191319},
				{173192387,575239487},
				{821231201,809260127},
				{127193351,255365245},
				{255363501,191367446},
				{359191549,631320383},
				{255191459,255195383},
				{808257211,442267447},
				{445244447,442267447},
				{831383317,442267447},
				{180192292,79192291},
				{383230071,442267447},
				{255191447,442267447},
				{639328447,191217671},
				{211192381,442267447},
				{717255789,731256832},
				{746005191,746005319},
				{255667,255667},
				{999220541,831213225},
				{703395668,255365245},
				{211192365,442267447},
				{575347959,63191201},
				{895396767,444319633},
				{831383372,454237192},
				{127191383,63191201},
				{703391535,442267447},
				{639383769,831213225},
				{836384540,191217671},
				{167192383,442267447},
				{63395085,442267447},
				{767259505,442267447},
				{639383767,831213225},
				{191366566,191217671},
				{895277971,959239839},
				{959487999,191217671},
				{657320447,81384387},
				{511318191,81384387},
				{767255404,191217671},
				{319237598,316256599},
				{497253241,191217671},
				{671319768,442267447},
				{767257447,447234275},
				{575327383,442267447},
				{553289255,511319084},
				{345203319,345203319},
				{291192753,350191940},
				{678319746,442267447},
				{746005127,746005063},
				{831383421,442267447},
				{167192327,79192291},
				{630319971,442267447},
				{83192321,79192291},
				{127191405,442267447},
				{999196019,442267447},
				{194305,79192291},
				{565239475,558250408},
				{310191478,267190427},
				{319357959,444319633},
				{575319365,255365245},
				{665313448,383223165},
				{322194470,319195472},
				{920256968,959239839},
				{255197231,442267447},
				{81384387,442267447},
				{191183999,255190937},
				{831383191,442267447},
				{454237192,191217671},
				{284291053,277290064},
				{255356965,63357993},
				{383361476,575239487},
				{447210937,447234275},
				{140384064,191372201},
				{703407821,442267447},
				{831213225,191217671},
				{831211177,809260127},
				{447358351,191217671},
				{255356959,63191201},
				{447356291,631320383},
				{223185511,231192559},
				{746005319,746005447},
				{191183957,191217671},
				{716385667,442267447},
				{801384695,447234275},
				{443319647,63191201},
				{280229767,280229767},
				{262191252,442267447},
				{177192375,180192292},
				{177192373,180192292},
				{868383993,651384999},
				{755255871,63191201},
				{127373483,442267447},
				{63192314,83192321},
				{383361511,63191201},
				{513313191,255226001},
				{895212059,442267447},
				{383207831,442267447},
				{721384895,831213225},
				{895228447,447234275},
				{447358447,454237192},
				{255365245,191217671},
				{511347005,63191201},
				{479222745,466218770},
				{348191626,442267447},
				{999232709,959239839},
				{383207833,63191201},
				{319364185,191217671},
				{226214445,215223439},
				{959403639,609313370},
				{191181885,191217671},
				{831383255,442267447},
				{255191127,442267447},
				{132192390,255365245},
				{895396401,447234275},
				{746005255,746005191},
				{735255713,767255703},
				{705379704,442267447},
				{766255832,454237192},
				{127176929,442267447},
				{207182639,211182639},
				{267190427,191217671},
				{575239487,191217671},
				{319227003,609313370},
				{331191490,255365245},
				{784257334,784257334},
				{511234545,442267447},
				{959403684,383359635},
				{959407806,63191201},
				{689319555,442267447},
				{639373703,442267447},
				{170192319,442267447},
				{959405748,127373483},
				{255365284,442267447},
				{575335895,442267447},
				{1269646,270638},
				{63175773,442267447},
				{686317384,831213225},
				{639383999,454237192},
				{255191187,444319633},
				{959413897,191217671},
				{511300063,442267447},
				{255226001,831213225},
				{959413895,191217671},
				{348191594,63191201},
				{348191596,63191201},
				{319364262,442267447},
				{746005447,746005959},
				{959239839,959239839},
				{748385064,442267447},
				{127375395,191217671},
				{702385767,703383767},
				{831215153,831213225},
				{277192517,350191940},
				{255195383,442267447},
				{447260001,447255015},
				{215192349,255191319},
				{511234475,511234475},
				{895396487,191217671},
				{831383127,442267447},
				{255191255,442267447},
				{448220501,442267447},
				{895216319,831213225},
				{447212867,447234275},
				{634296869,442267447},
				{63357993,442267447},
				{714255741,444319633},
				{746005383,746005319},
				{262191186,255191187},
				{262191188,442267447},
				{639361511,191217671}};
		
		ArrayList<Long> uniqueValues = new ArrayList<Long>();
		int newCount =0;
		for(int i=0;i<endresult.length; i++) {
			if(!uniqueValues.contains(endresult[i][1])) {
				uniqueValues.add(endresult[i][1]);
				System.out.println(endresult[i][1]+" "+newCount);
				newCount++;
			}
		}
		*/
	}
}
