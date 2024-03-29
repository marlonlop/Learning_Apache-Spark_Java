package com.virtualpairprogrammers;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import org.codehaus.janino.Java;
import org.omg.CORBA.INTERNAL;
import scala.Tuple2;

/**
 * This class is used in the chapter late in the course where we analyse viewing figures.
 * You can ignore until then.
 */
public class ViewingFigures 
{
	@SuppressWarnings("resource")
	public static void main(String[] args)
	{
		System.setProperty("hadoop.home.dir", "c:/hadoop");
		Logger.getLogger("org.apache").setLevel(Level.WARN);

		SparkConf conf = new SparkConf().setAppName("startingSpark").setMaster("local[*]");
		JavaSparkContext sc = new JavaSparkContext(conf);
		
		// Use true to use hardcoded data identical to that in the PDF guide.
		boolean testMode = false;
		
		JavaPairRDD<Integer, Integer> viewData = setUpViewDataRdd(sc, testMode);
		JavaPairRDD<Integer, Integer> chapterData = setUpChapterDataRdd(sc, testMode);
		JavaPairRDD<Integer, String> titlesData = setUpTitlesDataRdd(sc, testMode);

		// solution

//		step 1 - remove duplicated views

		JavaPairRDD<Integer, Integer> chapterCount = chapterData.mapToPair(row -> new Tuple2<>(row._2, 1))
			.reduceByKey( (val1, val2) -> val1 + val2 );

		viewData = viewData.distinct();


//		step 2 - get courseId into the RDD

//		viewData = viewData.mapToPair( row -> new Tuple2<>(row._2, row._1));
		viewData = viewData.mapToPair( Tuple2::swap );
		JavaPairRDD<Integer, Tuple2<Integer, Integer>> joinedRdd = viewData.join(chapterData);

//		step 3 - dropping chapterId

		JavaPairRDD<Tuple2<Integer, Integer>, Long> step3 = joinedRdd.mapToPair( row -> {
			Integer userId = row._2._1;
			Integer courseId = row._2._2;
			return new Tuple2<>(new Tuple2<>(userId, courseId), 1L);
		});

//		step 4 - reduce by key to obtain count of viewed chapters per user

		JavaPairRDD<Tuple2<Integer, Integer>, Long> step4 = step3.reduceByKey((val1, val2) -> val1 + val2);

//		step 5 - remove user ids
		JavaPairRDD<Integer, Long> step5 = step4.mapToPair(row -> new Tuple2<>(row._1._2, row._2));

//		step 6 - add in total chapter count
		JavaPairRDD<Integer, Tuple2<Long, Integer>> step6 = step5.join(chapterCount);

//		step 7 - determine watch ration of courses per user

//		JavaRDD<Double> step7 = step6.map(row -> (double) row._2._1 / row._2._2);
		// we can achieve the same by using mapValues
		JavaPairRDD<Integer, Double> step7 = step6.mapValues(value -> (double)value._1 / value._2);

//		step 8 - convert to scores

		JavaPairRDD<Integer, Long> step8 = step7.mapValues(val -> {
			if(val > 0.9) return 10L;
			if(val > 0.5) return 4L;
			if(val > 0.25) return 2L;
			return 0L;
		});

//		step 9 - reduce by key to obtain total scoreby course
		JavaPairRDD<Integer, Long> step9 = step8.reduceByKey((val1, val2) -> val1 + val2);

//		step 10
		JavaPairRDD<Integer, Tuple2<Long, String>> step10 = step9.join(titlesData);
		JavaPairRDD<Long, String> lastStep = step10.mapToPair(row -> new Tuple2<>(row._2._1, row._2._2));
		lastStep.sortByKey(false).collect().forEach(System.out::println);


		sc.close();
	}



	private static JavaPairRDD<Integer, String> setUpTitlesDataRdd(JavaSparkContext sc, boolean testMode) {
		
		if (testMode)
		{
			// (chapterId, title)
			List<Tuple2<Integer, String>> rawTitles = new ArrayList<>();
			rawTitles.add(new Tuple2<>(1, "How to find a better job"));
			rawTitles.add(new Tuple2<>(2, "Work faster harder smarter until you drop"));
			rawTitles.add(new Tuple2<>(3, "Content Creation is a Mug's Game"));
			return sc.parallelizePairs(rawTitles);
		}
		return sc.textFile("src/main/resources/viewing figures/titles.csv")
				                                    .mapToPair(commaSeparatedLine -> {
														String[] cols = commaSeparatedLine.split(",");
														return new Tuple2<Integer, String>(new Integer(cols[0]),cols[1]);
				                                    });
	}

	private static JavaPairRDD<Integer, Integer> setUpChapterDataRdd(JavaSparkContext sc, boolean testMode) {
		
		if (testMode)
		{
			// (chapterId, (courseId, courseTitle))
			List<Tuple2<Integer, Integer>> rawChapterData = new ArrayList<>();
			rawChapterData.add(new Tuple2<>(96,  1));
			rawChapterData.add(new Tuple2<>(97,  1));
			rawChapterData.add(new Tuple2<>(98,  1));
			rawChapterData.add(new Tuple2<>(99,  2));
			rawChapterData.add(new Tuple2<>(100, 3));
			rawChapterData.add(new Tuple2<>(101, 3));
			rawChapterData.add(new Tuple2<>(102, 3));
			rawChapterData.add(new Tuple2<>(103, 3));
			rawChapterData.add(new Tuple2<>(104, 3));
			rawChapterData.add(new Tuple2<>(105, 3));
			rawChapterData.add(new Tuple2<>(106, 3));
			rawChapterData.add(new Tuple2<>(107, 3));
			rawChapterData.add(new Tuple2<>(108, 3));
			rawChapterData.add(new Tuple2<>(109, 3));
			return sc.parallelizePairs(rawChapterData);
		}

		return sc.textFile("src/main/resources/viewing figures/chapters.csv")
													  .mapToPair(commaSeparatedLine -> {
															String[] cols = commaSeparatedLine.split(",");
															return new Tuple2<Integer, Integer>(new Integer(cols[0]), new Integer(cols[1]));
													  	});
	}

	private static JavaPairRDD<Integer, Integer> setUpViewDataRdd(JavaSparkContext sc, boolean testMode) {
		
		if (testMode)
		{
			// Chapter views - (userId, chapterId)
			List<Tuple2<Integer, Integer>> rawViewData = new ArrayList<>();
			rawViewData.add(new Tuple2<>(14, 96));
			rawViewData.add(new Tuple2<>(14, 97));
			rawViewData.add(new Tuple2<>(13, 96));
			rawViewData.add(new Tuple2<>(13, 96));
			rawViewData.add(new Tuple2<>(13, 96));
			rawViewData.add(new Tuple2<>(14, 99));
			rawViewData.add(new Tuple2<>(13, 100));
			return  sc.parallelizePairs(rawViewData);
		}
		
		return sc.textFile("src/main/resources/viewing figures/views-*.csv")
				     .mapToPair(commaSeparatedLine -> {
				    	 String[] columns = commaSeparatedLine.split(",");
				    	 return new Tuple2<Integer, Integer>(new Integer(columns[0]), new Integer(columns[1]));
				     });
	}
}
