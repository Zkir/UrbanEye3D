
import csv
import random
import sys

def create_sample(input_filename, output_filename, k):
    """
    Creates a random sample of k lines from a CSV file using reservoir sampling.
    This method is memory-efficient as it reads the file only once.

    Args:
        input_filename (str): The path to the large CSV file.
        output_filename (str): The path to save the sampled CSV file.
        k (int): The number of random samples to take.
    """
    print(f"Opening '{input_filename}' to create a sample of {k} records...")

    try:
        with open(input_filename, 'r', newline='', encoding='utf-8') as infile:
            reader = csv.reader(infile)

            # Read the header
            header = next(reader)

            # Create the reservoir with the first k lines
            reservoir = []
            for i, row in enumerate(reader):
                if i < k:
                    reservoir.append(row)
                else:
                    break

            if len(reservoir) < k:
                print(f"Warning: The file has fewer than {k} records. The sample will contain all records.")

            # Continue from where we left off, replacing items in the reservoir
            # with decreasing probability.
            line_num = k
            for row in reader:
                line_num += 1
                if line_num % 100000 == 0:
                    print(f"Scanned {line_num} lines...", end='\r')

                j = random.randint(0, line_num - 1)
                if j < k:
                    reservoir[j] = row

            print(f"\nScan complete. Writing {len(reservoir)} sampled records to '{output_filename}'...")

            # Write the results to the output file
            with open(output_filename, 'w', newline='', encoding='utf-8') as outfile:
                writer = csv.writer(outfile)
                writer.writerow(header)
                writer.writerows(reservoir)

            print("Done.")

    except FileNotFoundError:
        print(f"Error: Input file '{input_filename}' not found.")
        sys.exit(1)
    except Exception as e:
        print(f"An error occurred: {e}")
        sys.exit(1)

if __name__ == "__main__":
    INPUT_FILE = 'trees.csv'
    OUTPUT_FILE = 'trees_sample.csv'
    SAMPLE_SIZE = 100000
    
    create_sample(INPUT_FILE, OUTPUT_FILE, SAMPLE_SIZE)
